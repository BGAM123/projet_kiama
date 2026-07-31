package com.docuai.api.service;

import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.rag.SimilaritySearchService;
import com.docuai.ai.service.GenerationOrchestrator;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.ai.service.StructuralValidator;
import com.docuai.api.config.GenerationProperties;
import com.docuai.core.model.DocumentChunk;
import com.docuai.core.model.DocumentGenere;
import com.docuai.core.model.DocumentGenereStatut;
import com.docuai.core.model.DocumentReference;
import com.docuai.core.model.DocumentStructure;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.GenerationSectionNode;
import com.docuai.core.model.StructureNode;
import com.docuai.core.repository.DocumentGenereRepository;
import com.docuai.core.repository.DocumentReferenceRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Déroule la génération section par section et pousse la progression au
 * client via SSE (Bloc 6) — un événement JSON par ligne
 * ({@code {"type":"progress"|"section"|"done", ...}}), même contrat que la
 * simulation déjà stabilisée côté frontend
 * ({@code frontend/lib/api/generator.ts}, {@code StreamEvent}). Chaque
 * section correspond à un appel bloquant complet à
 * {@link GenerationOrchestrator#generate} (pas un flux token par token :
 * c'est la progression PAR SECTION qui est diffusée, pas le texte en train
 * de s'écrire) — suffisant pour reproduire le comportement déjà attendu par
 * l'UI, et plus simple/robuste à streamer par HTTP qu'un relais de deltas.
 */
@Service
public class GenerationStreamService {

    private static final Logger log = LoggerFactory.getLogger(GenerationStreamService.class);
    private static final long EMITTER_TIMEOUT_MS = 300_000L;

    private final DocumentGenereRepository documentGenereRepository;
    private final DocumentStructureRepository documentStructureRepository;
    private final DocumentReferenceRepository documentReferenceRepository;
    private final GenerationService generationService;
    private final PromptBuilder promptBuilder;
    private final StructuralValidator structuralValidator;
    private final GenerationOrchestrator generationOrchestrator;
    private final SimilaritySearchService similaritySearchService;
    private final GenerationProperties generationProperties;

    public GenerationStreamService(DocumentGenereRepository documentGenereRepository,
                                    DocumentStructureRepository documentStructureRepository,
                                    DocumentReferenceRepository documentReferenceRepository,
                                    GenerationService generationService,
                                    PromptBuilder promptBuilder,
                                    StructuralValidator structuralValidator,
                                    GenerationOrchestrator generationOrchestrator,
                                    SimilaritySearchService similaritySearchService,
                                    GenerationProperties generationProperties) {
        this.documentGenereRepository = documentGenereRepository;
        this.documentStructureRepository = documentStructureRepository;
        this.documentReferenceRepository = documentReferenceRepository;
        this.generationService = generationService;
        this.promptBuilder = promptBuilder;
        this.structuralValidator = structuralValidator;
        this.generationOrchestrator = generationOrchestrator;
        this.similaritySearchService = similaritySearchService;
        this.generationProperties = generationProperties;
    }

    public SseEmitter createEmitter() {
        return new SseEmitter(EMITTER_TIMEOUT_MS);
    }

    /**
     * Tourne sur {@code generationExecutor} (AsyncConfig) — appelée depuis le
     * contrôleur (bean externe), donc le proxy Spring AOP de {@code @Async}
     * s'applique normalement (contrairement au cas d'auto-invocation interne
     * documenté sur {@code DocumentTypeExtractionService} au Bloc 4).
     */
    @Async("generationExecutor")
    public void run(UUID documentId, SseEmitter emitter, UUID requestingUserId, boolean isAdmin) {
        try {
            DocumentGenere document = generationService.findEntity(documentId);
            generationService.checkOwnership(document, requestingUserId, isAdmin);
            runGeneration(document, emitter);
        } catch (Exception e) {
            log.error("Échec du flux de génération {}", documentId, e);
            emitter.completeWithError(e);
        }
    }

    private void runGeneration(DocumentGenere document, SseEmitter emitter) {
        DocumentType documentType = document.getDocumentType();
        List<StructureNode> expectedStructure = documentStructureRepository.findByDocumentType_Id(documentType.getId())
                .map(DocumentStructure::getArbreJson)
                .orElse(List.of())
                .stream()
                .filter(n -> !"cover".equals(n.getType()))
                .toList();

        String systemPrompt = promptBuilder.buildSystemPrompt(
                expectedStructure,
                document.getLangue() != null ? document.getLangue().name() : null,
                document.getTon() != null ? document.getTon().name() : null,
                document.getLongueurCible() != null ? document.getLongueurCible().name() : null);

        List<UUID> referenceIds = documentReferenceRepository
                .findByConversation_IdOrderByDateImportAsc(document.getConversation().getId()).stream()
                .map(DocumentReference::getId)
                .toList();

        List<GenerationSectionNode> sections = document.getSections();
        int total = sections.size();
        StringBuilder accumulated = new StringBuilder();
        boolean anyFailure = false;

        for (int i = 0; i < total; i++) {
            GenerationSectionNode section = sections.get(i);
            emitEvent(emitter, event("progress", i, total, section.getLabel(), null));
            section.setStatus("GENERATING");

            String sectionContent;
            try {
                sectionContent = generateSectionWithRetry(document, section, systemPrompt, referenceIds);
                section.setStatus("DONE");
            } catch (AiProviderException e) {
                log.warn("Échec de génération de la section « {} » du document {} : {}", section.getLabel(), document.getId(), e.getMessage());
                section.setStatus("FAILED");
                sectionContent = "";
                anyFailure = true;
            }
            section.setContent(sectionContent);

            if (!sectionContent.isBlank()) {
                if (!accumulated.isEmpty()) {
                    accumulated.append("\n\n");
                }
                accumulated.append("## ").append(section.getLabel()).append('\n').append(sectionContent);
            }
            document.setContenu(accumulated.toString());
            document.setDateMaj(LocalDateTime.now());
            // documentGenereRepository.save(...) est nativement transactionnel
            // (Spring Data JPA porte @Transactional sur SimpleJpaRepository) —
            // pas besoin d'un @Transactional supplémentaire ici, qui serait de
            // toute façon inopérant par auto-invocation (même piège que celui
            // évité sur DocumentTypeExtractionService au Bloc 4).
            documentGenereRepository.save(document);

            emitEvent(emitter, event("section", i, total, section.getLabel(), sectionContent));
        }

        StructuralValidator.ValidationResult validation = structuralValidator.validate(accumulated.toString(), expectedStructure);
        if (!validation.valid()) {
            log.warn("Document généré {} : titres attendus manquants {}", document.getId(), validation.missingHeadings());
        }

        document.setStatut(anyFailure ? DocumentGenereStatut.ECHEC : DocumentGenereStatut.GENERE);
        document.setDateMaj(LocalDateTime.now());
        documentGenereRepository.save(document);

        emitEvent(emitter, event("done", null, total, null, null));
        emitter.complete();
    }

    private String generateSectionWithRetry(DocumentGenere document, GenerationSectionNode section,
                                             String systemPrompt, List<UUID> referenceIds) {
        int maxAttempts = Math.max(1, generationProperties.getSectionRetryAttempts() + 1);
        AiProviderException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return generateSection(document, section, systemPrompt, referenceIds);
            } catch (AiProviderException e) {
                lastError = e;
                log.warn("Tentative {}/{} échouée pour la section « {} » : {}", attempt, maxAttempts, section.getLabel(), e.getMessage());
            }
        }
        throw lastError;
    }

    private String generateSection(DocumentGenere document, GenerationSectionNode section,
                                    String systemPrompt, List<UUID> referenceIds) {
        String query = section.getLabel() + (document.getPromptUtilisateur() != null ? " " + document.getPromptUtilisateur() : "");
        List<DocumentChunk> referenceChunks = referenceIds.isEmpty() ? List.of() : similaritySearchService.search(query, referenceIds);

        StringBuilder instructions = new StringBuilder("Rédige uniquement la section suivante du document, sans répéter les autres sections : « ")
                .append(section.getLabel()).append(" ».");
        if (document.getPromptUtilisateur() != null && !document.getPromptUtilisateur().isBlank()) {
            instructions.append(" Instructions générales de l'utilisateur : ").append(document.getPromptUtilisateur());
        }
        String userPrompt = promptBuilder.buildUserPrompt(instructions.toString(), referenceChunks);

        GenerationRequest request = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build();
        return generationOrchestrator.generate(request).getContent();
    }

    private Map<String, Object> event(String type, Integer sectionIndex, Integer total, String sectionLabel, String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        if (sectionIndex != null) payload.put("sectionIndex", sectionIndex);
        if (total != null) payload.put("total", total);
        if (sectionLabel != null) payload.put("sectionLabel", sectionLabel);
        if (content != null) payload.put("content", content);
        return payload;
    }

    private void emitEvent(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new UncheckedIOException("Client SSE déconnecté.", e);
        }
    }
}
