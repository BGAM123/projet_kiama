package com.docuai.api.service;

import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.service.GenerationOrchestrator;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.ai.service.SkeletonContentGuard;
import com.docuai.ai.service.SkeletonResponseParser;
import com.docuai.api.dto.DocumentTypeDTO;
import com.docuai.api.dto.GenerateDocumentTypeRequest;
import com.docuai.api.event.DocumentTypeGenerationCompletedEvent;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.DocumentTypeMapper;
import com.docuai.core.model.Categorie;
import com.docuai.core.model.DocumentStructure;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.DocumentTypeStatut;
import com.docuai.core.model.StructureNode;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.CategorieRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestration du flux "décrire en texte -> squelette généré par IA"
 * (Document Type) : coexiste avec {@link DocumentTypeExtractionService}
 * (import de fichier + extraction déterministe) plutôt que de le remplacer —
 * même modèle de sortie ({@link DocumentStructure#getArbreJson()}), même
 * cycle de statuts ({@link DocumentTypeStatut}), seule la façon de produire
 * l'arbre diffère (appel LLM au lieu de parsing POI/PDFBox).
 * <p>
 * Contrairement à l'extraction, la sortie du LLM n'est pas garantie
 * structurellement correcte : {@link SkeletonResponseParser} peut échouer à
 * parser du JSON malformé, et {@link SkeletonContentGuard} peut détecter du
 * contenu rédigé au lieu d'un squelette. Les deux déclenchent une nouvelle
 * tentative avec un prompt correctif, bornée à {@link #MAX_ATTEMPTS} essais —
 * épuisée, le Document Type passe en {@code ECHEC_EXTRACTION} (même statut
 * que l'échec d'extraction déterministe, réutilisé pour ne pas dupliquer le
 * cycle de vie).
 */
@Service
public class DocumentTypeGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentTypeGenerationService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentStructureRepository documentStructureRepository;
    private final CategorieRepository categorieRepository;
    private final DocumentTypeMapper documentTypeMapper;
    private final GenerationOrchestrator generationOrchestrator;
    private final PromptBuilder promptBuilder;
    private final SkeletonResponseParser skeletonResponseParser;
    private final SkeletonContentGuard skeletonContentGuard;
    private final DocumentTypeContextCacheService documentTypeContextCacheService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentTypeGenerationService(DocumentTypeRepository documentTypeRepository,
                                          DocumentStructureRepository documentStructureRepository,
                                          CategorieRepository categorieRepository,
                                          DocumentTypeMapper documentTypeMapper,
                                          GenerationOrchestrator generationOrchestrator,
                                          PromptBuilder promptBuilder,
                                          SkeletonResponseParser skeletonResponseParser,
                                          SkeletonContentGuard skeletonContentGuard,
                                          DocumentTypeContextCacheService documentTypeContextCacheService,
                                          ApplicationEventPublisher eventPublisher) {
        this.documentTypeRepository = documentTypeRepository;
        this.documentStructureRepository = documentStructureRepository;
        this.categorieRepository = categorieRepository;
        this.documentTypeMapper = documentTypeMapper;
        this.generationOrchestrator = generationOrchestrator;
        this.promptBuilder = promptBuilder;
        this.skeletonResponseParser = skeletonResponseParser;
        this.skeletonContentGuard = skeletonContentGuard;
        this.documentTypeContextCacheService = documentTypeContextCacheService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DocumentTypeDTO generate(GenerateDocumentTypeRequest request, Utilisateur currentUser) {
        Categorie categorie = categorieRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Catégorie introuvable : " + request.getCategoryId()));

        DocumentType documentType = DocumentType.builder()
                .nom(request.getName())
                .description(request.getDescription())
                .categorie(categorie)
                .statut(DocumentTypeStatut.EN_EXTRACTION)
                .version(1)
                .utilisateurCreateur(currentUser)
                .build();
        documentType = documentTypeRepository.save(documentType);

        boolean quotaExceeded = runGeneration(documentType, request.getDescription());

        documentType = documentTypeRepository.save(documentType);
        eventPublisher.publishEvent(new DocumentTypeGenerationCompletedEvent(
                documentType.getId(), currentUser.getId(), documentType.getNom(),
                documentType.getStatut() == DocumentTypeStatut.STRUCTURE_EXTRAITE, quotaExceeded));

        return documentTypeMapper.toDto(documentType);
    }

    /** @return vrai si au moins une tentative a échoué avec un quota/rate limit IA dépassé (HTTP 429). */
    private boolean runGeneration(DocumentType documentType, String description) {
        String baseSystemPrompt = promptBuilder.buildSkeletonSystemPrompt(description);
        List<String> lastViolations = List.of();
        boolean quotaExceeded = false;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String systemPrompt = lastViolations.isEmpty()
                    ? baseSystemPrompt
                    : baseSystemPrompt + skeletonContentGuard.buildCorrectivePrompt(lastViolations);

            try {
                GenerationRequest generationRequest = GenerationRequest.builder()
                        .systemPrompt(systemPrompt)
                        .userPrompt("Génère le squelette JSON pour ce type de document, sans aucun contenu rédigé.")
                        .build();
                String raw = generationOrchestrator.generate(generationRequest).getContent();
                List<StructureNode> tree = skeletonResponseParser.parse(raw);

                SkeletonContentGuard.ValidationResult validation = skeletonContentGuard.validate(tree);
                if (validation.valid()) {
                    persistStructure(documentType, tree);
                    documentType.setStatut(DocumentTypeStatut.STRUCTURE_EXTRAITE);
                    return false;
                }
                log.warn("Squelette IA non conforme pour le document type {} (tentative {}/{}) : {}",
                        documentType.getId(), attempt, MAX_ATTEMPTS, validation.violations());
                lastViolations = validation.violations();
            } catch (SkeletonResponseParser.SkeletonParseException | AiProviderException e) {
                if (e instanceof AiProviderException aiException && aiException.isQuotaExceeded()) {
                    quotaExceeded = true;
                }
                log.warn("Échec de génération de squelette IA pour le document type {} (tentative {}/{}) : {}",
                        documentType.getId(), attempt, MAX_ATTEMPTS, e.getMessage());
                lastViolations = List.of(e.getMessage() == null ? "erreur inconnue" : e.getMessage());
            }
        }

        documentType.setStatut(DocumentTypeStatut.ECHEC_EXTRACTION);
        return quotaExceeded;
    }

    /** Même convention que {@code DocumentTypeExtractionService} : un nœud "cover" préfixe systématiquement l'arbre. */
    private void persistStructure(DocumentType documentType, List<StructureNode> generatedTree) {
        List<StructureNode> tree = new ArrayList<>();
        tree.add(StructureNode.builder().id("cover").type("cover").label(documentType.getNom()).build());
        tree.addAll(generatedTree);

        boolean hasToc = tree.stream().anyMatch(n -> "heading".equals(n.getType()) && n.getLevel() != null && n.getLevel() == 1);

        DocumentStructure structure = documentStructureRepository.findByDocumentType_Id(documentType.getId())
                .orElseGet(() -> DocumentStructure.builder().documentType(documentType).build());
        structure.setArbreJson(tree);
        structure.setPossedeToc(hasToc);
        structure.setSource("AI_GENERATED");
        documentStructureRepository.save(structure);
        documentTypeContextCacheService.evict(documentType.getId());
    }
}
