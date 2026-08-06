package com.docuai.api.service;

import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.rag.SimilaritySearchService;
import com.docuai.ai.service.GenerationOrchestrator;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.ai.service.SectionConstraintValidator;
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
import com.docuai.export.ExportContent;
import com.docuai.export.ExportFormat;
import com.docuai.export.ExportService;
import com.docuai.export.ExportedFile;
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Déroule la génération section par section et pousse la progression au
 * client via SSE — un événement JSON par ligne
 * ({@code {"type":"progress"|"delta"|"section"|"done"|"error", ...}}), même
 * contrat que celui consommé côté frontend
 * ({@code frontend/lib/api/generator.ts}, {@code StreamEvent}). Chaque
 * section est désormais générée via
 * {@link GenerationOrchestrator#streamGenerate} (texte qui s'écrit
 * progressivement, un événement {@code delta} par fragment reçu du
 * fournisseur IA) plutôt qu'un unique appel bloquant — {@link #generateSection}
 * bloque le thread {@code @Async} courant jusqu'à la fin du flux
 * ({@code Flux#blockLast}), ce qui reste sûr ici : ce thread appartient à
 * {@code generationExecutor} (AsyncConfig), pas à une boucle d'événements
 * réactive, donc rien n'est jamais bloqué qui ne devrait pas l'être.
 */
@Service
public class GenerationStreamService {

    private static final Logger log = LoggerFactory.getLogger(GenerationStreamService.class);
    private static final long EMITTER_TIMEOUT_MS = 300_000L;
    /** Marge au-delà du timeout SSE : couvre le temps de traitement après le dernier octet reçu par le client avant que le verrou n'expire de lui-même. */
    private static final Duration LOCK_TTL = Duration.ofMillis(EMITTER_TIMEOUT_MS + 30_000L);

    private final DocumentGenereRepository documentGenereRepository;
    private final DocumentStructureRepository documentStructureRepository;
    private final DocumentReferenceRepository documentReferenceRepository;
    private final GenerationService generationService;
    private final PromptBuilder promptBuilder;
    private final StructuralValidator structuralValidator;
    private final GenerationOrchestrator generationOrchestrator;
    private final SimilaritySearchService similaritySearchService;
    private final GenerationProperties generationProperties;
    private final GenerationLockService generationLockService;
    private final SectionConstraintValidator sectionConstraintValidator;
    private final ExportService exportService;
    private final ObjectStorageService objectStorageService;
    private final MinioProperties minioProperties;

    public GenerationStreamService(DocumentGenereRepository documentGenereRepository,
                                    DocumentStructureRepository documentStructureRepository,
                                    DocumentReferenceRepository documentReferenceRepository,
                                    GenerationService generationService,
                                    PromptBuilder promptBuilder,
                                    StructuralValidator structuralValidator,
                                    GenerationOrchestrator generationOrchestrator,
                                    SimilaritySearchService similaritySearchService,
                                    GenerationProperties generationProperties,
                                    GenerationLockService generationLockService,
                                    SectionConstraintValidator sectionConstraintValidator,
                                    ExportService exportService,
                                    ObjectStorageService objectStorageService,
                                    MinioProperties minioProperties) {
        this.documentGenereRepository = documentGenereRepository;
        this.documentStructureRepository = documentStructureRepository;
        this.documentReferenceRepository = documentReferenceRepository;
        this.generationService = generationService;
        this.promptBuilder = promptBuilder;
        this.structuralValidator = structuralValidator;
        this.generationOrchestrator = generationOrchestrator;
        this.similaritySearchService = similaritySearchService;
        this.generationProperties = generationProperties;
        this.generationLockService = generationLockService;
        this.sectionConstraintValidator = sectionConstraintValidator;
        this.exportService = exportService;
        this.objectStorageService = objectStorageService;
        this.minioProperties = minioProperties;
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

            Optional<String> lockToken = generationLockService.tryAcquire(documentId, LOCK_TTL);
            if (lockToken.isEmpty()) {
                emitEvent(emitter, errorEvent("GENERATION_IN_PROGRESS", "Une génération est déjà en cours pour ce document."));
                emitter.complete();
                return;
            }
            try {
                runGeneration(document, emitter);
            } finally {
                generationLockService.release(documentId, lockToken.get());
            }
        } catch (Exception e) {
            log.error("Échec du flux de génération {}", documentId, e);
            emitter.completeWithError(e);
        }
    }

    private void runGeneration(DocumentGenere document, SseEmitter emitter) {
        DocumentType documentType = document.getDocumentType();
        Optional<DocumentStructure> structure = documentStructureRepository.findByDocumentType_Id(documentType.getId());
        List<StructureNode> expectedStructure = structure.map(DocumentStructure::getArbreJson)
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

        /*
         * Reprise après interruption : les sections déjà DONE d'un appel
         * précédent (crash, timeout SSE) forment nécessairement un préfixe
         * contigu — la boucle ci-dessous traite toujours les sections dans
         * l'ordre et s'arrête à la première non-DONE (FAILED y compris : elle
         * casse la contiguïté du préfixe, donc redevient elle-même le point
         * de reprise, et sera retentée). On rejoue leurs événements "section"
         * pour un client qui se reconnecte, puis on ne relance la génération
         * qu'à partir du premier index non-DONE.
         */
        int startIndex = 0;
        while (startIndex < total && "DONE".equals(sections.get(startIndex).getStatus())) {
            GenerationSectionNode alreadyDone = sections.get(startIndex);
            appendSection(accumulated, alreadyDone);
            emitEvent(emitter, event("section", startIndex, total, alreadyDone.getLabel(), alreadyDone.getContent()));
            startIndex++;
        }

        for (int i = startIndex; i < total; i++) {
            GenerationSectionNode section = sections.get(i);
            section.setStatus("GENERATING");

            String sectionContent;
            try {
                sectionContent = generateSectionWithRetry(document, section, systemPrompt, referenceIds, i, total, emitter);
                section.setStatus("DONE");
            } catch (AiProviderException e) {
                log.warn("Échec de génération de la section « {} » du document {} : {}", section.getLabel(), document.getId(), e.getMessage());
                section.setStatus("FAILED");
                sectionContent = "";
                anyFailure = true;
            }
            section.setContent(sectionContent);
            appendSection(accumulated, section);
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
        if (!anyFailure) {
            exportToObjectStorage(document, documentType, structure.orElse(null));
        }
        documentGenereRepository.save(document);

        emitEvent(emitter, event("done", null, total, null, null));
        emitter.complete();
    }

    /**
     * Export DOCX automatique vers MinIO ({@code bucket-exports}) une fois la
     * génération intégralement réussie — dégradation volontaire : un échec
     * ici (MinIO indisponible, etc.) ne remet pas en cause le statut GENERE
     * déjà acquis, {@code minioObjectKey} reste simplement absent (pas d'URL
     * de téléchargement pré-signée tant qu'un nouvel appel n'y parvient pas).
     * L'export manuel existant ({@code POST /api/v1/export}) reste disponible
     * en repli, pour un autre format ou après édition manuelle du contenu.
     */
    private void exportToObjectStorage(DocumentGenere document, DocumentType documentType, DocumentStructure structure) {
        try {
            ExportContent content = new ExportContent(documentType.getNom(), document.getContenu(),
                    structure != null ? structure.getHeaderText() : null,
                    structure != null ? structure.getFooterText() : null);
            ExportedFile file = exportService.export(content, ExportFormat.DOCX);
            String objectKey = "exports/" + document.getId() + "/" + file.filename();
            objectStorageService.upload(minioProperties.getBucketExports(), objectKey, file.content(), file.contentType());
            document.setMinioObjectKey(objectKey);
            document.setExportFormat(ExportFormat.DOCX.name());
        } catch (Exception e) {
            log.warn("Export automatique vers MinIO impossible pour le document généré {} : {}", document.getId(), e.getMessage());
        }
    }

    /**
     * Deux motifs de nouvelle tentative, cumulés dans la même boucle mais de
     * nature différente : panne IA transitoire ({@link AiProviderException},
     * déjà partiellement absorbée en amont par {@code @Retry} sur
     * {@code GenerationOrchestrator.streamGenerate}, cf. l'étape Resilience4j
     * réactive) et violation des contraintes de section
     * ({@link SectionConstraintValidator}) — cette dernière renvoie un prompt
     * correctif à la tentative suivante plutôt qu'un simple ré-essai à
     * l'identique. Épuisé (quelle qu'en soit la cause), on lève
     * {@link AiProviderException} : le catch existant dans
     * {@code runGeneration} marque la section FAILED sans bloquer les
     * suivantes.
     * <p>
     * Un événement {@code progress} est émis à CHAQUE tentative (pas
     * seulement la première) : c'est le signal pour le client de vider
     * l'aperçu en direct de cette section avant que les {@code delta} de la
     * nouvelle tentative n'arrivent — sans ça, un retry correctif ferait
     * apparaître le nouveau texte à la suite de l'ancien au lieu de le
     * remplacer.
     */
    private String generateSectionWithRetry(DocumentGenere document, GenerationSectionNode section,
                                             String systemPrompt, List<UUID> referenceIds,
                                             int sectionIndex, int total, SseEmitter emitter) {
        int maxAttempts = Math.max(1, generationProperties.getSectionRetryAttempts() + 1);
        boolean required = section.getRequired() == null || section.getRequired();
        AiProviderException lastError = null;
        List<String> lastViolations = List.of();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String correctivePrompt = lastViolations.isEmpty() ? null : buildCorrectivePrompt(lastViolations);
            emitEvent(emitter, event("progress", sectionIndex, total, section.getLabel(), null));
            try {
                String content = generateSection(document, section, systemPrompt, referenceIds, correctivePrompt, sectionIndex, total, emitter);
                SectionConstraintValidator.ValidationResult validation =
                        sectionConstraintValidator.validate(content, section.getConstraints(), required);
                if (validation.valid()) {
                    return content;
                }
                lastViolations = validation.violations();
                lastError = null;
                log.warn("Contraintes non respectées pour la section « {} » (tentative {}/{}) : {}",
                        section.getLabel(), attempt, maxAttempts, validation.violations());
            } catch (AiProviderException e) {
                lastError = e;
                lastViolations = List.of();
                log.warn("Tentative {}/{} échouée pour la section « {} » : {}", attempt, maxAttempts, section.getLabel(), e.getMessage());
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new AiProviderException("Contraintes non respectées après " + maxAttempts + " tentative(s) pour la section « "
                + section.getLabel() + " » : " + String.join(" ; ", lastViolations));
    }

    private String buildCorrectivePrompt(List<String> violations) {
        return "Ta réponse précédente ne respectait pas ces contraintes, corrige-la en conséquence : " + String.join(" ; ", violations) + ".";
    }

    /**
     * Consomme {@link GenerationOrchestrator#streamGenerate} fragment par
     * fragment : chaque {@code Chunk} non vide devient un événement SSE
     * {@code delta} (contenu incrémental, pas le texte accumulé — au client
     * de concaténer), pendant que ce thread {@code @Async} reste bloqué sur
     * le flux ({@code blockLast}) jusqu'à sa fin ou son erreur. Les appels à
     * {@code emitEvent} dans {@code doOnNext} s'exécutent sur le thread
     * d'émission du flux réactif (pas forcément celui-ci), mais toujours en
     * série (garantie Reactive Streams sur une souscription unique) — jamais
     * en concurrence avec un autre appel à {@code emitEvent} tant que cette
     * méthode elle-même n'est jamais appelée en parallèle (elle ne l'est pas :
     * une section à la fois, cf. la boucle de {@code runGeneration}).
     */
    private String generateSection(DocumentGenere document, GenerationSectionNode section,
                                    String systemPrompt, List<UUID> referenceIds, String correctivePrompt,
                                    int sectionIndex, int total, SseEmitter emitter) {
        String query = section.getLabel() + (document.getPromptUtilisateur() != null ? " " + document.getPromptUtilisateur() : "");
        List<DocumentChunk> referenceChunks = referenceIds.isEmpty() ? List.of() : similaritySearchService.search(query, referenceIds);

        StringBuilder instructions = new StringBuilder("Rédige uniquement la section suivante du document, sans répéter les autres sections : « ")
                .append(section.getLabel()).append(" ».");
        if ("table".equals(section.getType()) && section.getColumns() != null && !section.getColumns().isEmpty()) {
            instructions.append(" Cette section est un tableau : réponds uniquement avec un tableau au format Markdown ")
                    .append("(syntaxe `| colonne | colonne |` avec une ligne de séparation `|---|---|`), en utilisant exactement ")
                    .append("ces colonnes, dans cet ordre : ")
                    .append(String.join(", ", section.getColumns()))
                    .append(". Ajoute les lignes de données pertinentes ; pas de texte avant ou après le tableau.");
        }
        if (document.getPromptUtilisateur() != null && !document.getPromptUtilisateur().isBlank()) {
            instructions.append(" Instructions générales de l'utilisateur : ").append(document.getPromptUtilisateur());
        }
        if (correctivePrompt != null && !correctivePrompt.isBlank()) {
            instructions.append(' ').append(correctivePrompt);
        }
        String userPrompt = promptBuilder.buildUserPrompt(instructions.toString(), referenceChunks);

        GenerationRequest request = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build();

        StringBuilder accumulated = new StringBuilder();
        generationOrchestrator.streamGenerate(request)
                .doOnNext(chunk -> {
                    String piece = chunk.getContent();
                    if (piece != null && !piece.isEmpty()) {
                        accumulated.append(piece);
                        emitEvent(emitter, event("delta", sectionIndex, total, section.getLabel(), piece));
                    }
                })
                .blockLast();
        return accumulated.toString();
    }

    /** Ajoute le contenu d'une section (déjà générée ou rejouée depuis une reprise) à l'assemblage {@code document.contenu} — ignore les sections vides (FAILED). */
    private void appendSection(StringBuilder accumulated, GenerationSectionNode section) {
        String content = section.getContent();
        if (content == null || content.isBlank()) {
            return;
        }
        if (!accumulated.isEmpty()) {
            accumulated.append("\n\n");
        }
        accumulated.append(headingPrefix(section)).append(section.getLabel()).append('\n').append(content);
    }

    /**
     * Préfixe Markdown ajouté devant le label de section lors de
     * l'assemblage de {@code document.contenu}. Seules les sections
     * {@code type == "heading"} reçoivent un vrai niveau de titre (repris de
     * la structure extraite) ; paragraphes et tableaux n'en ont pas besoin
     * (leur contenu généré porte déjà sa propre forme). {@code type == null}
     * couvre les générations créées avant l'ajout de ce champ — comportement
     * historique conservé (tout en H2).
     */
    private String headingPrefix(GenerationSectionNode section) {
        if (section.getType() == null) {
            return "## ";
        }
        if (!"heading".equals(section.getType())) {
            return "";
        }
        int level = section.getLevel() != null ? section.getLevel() : 2;
        return "#".repeat(Math.max(1, Math.min(level, 6))) + " ";
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

    private Map<String, Object> errorEvent(String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", code);
        payload.put("message", message);
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
