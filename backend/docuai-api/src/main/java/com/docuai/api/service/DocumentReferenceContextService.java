package com.docuai.api.service;

import com.docuai.ai.rag.SimilaritySearchService;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.core.model.DocumentChunk;
import com.docuai.core.model.DocumentReference;
import com.docuai.core.repository.DocumentReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Enrichit un prompt utilisateur avec les extraits les plus pertinents des
 * documents de référence attachés à un {@link com.docuai.core.model.Document}
 * (recherche par similarité pgvector, {@link SimilaritySearchService}) — point
 * unique partagé par {@code DocumentService#improveSelection} et {@code
 * DocumentSectionService#improve}, les deux appels "Améliorer avec l'IA" de
 * l'éditeur, pour éviter de dupliquer la dégradation silencieuse ci-dessous.
 * <p>
 * Sans document de référence attaché, ou en cas d'échec (ex. {@code
 * OPENAI_API_KEY} absente — {@code EmbeddingService} est câblé sur OpenAI
 * uniquement, indépendamment du fournisseur par défaut choisi en admin),
 * l'enrichissement est silencieusement ignoré : le prompt original est
 * renvoyé tel quel, jamais d'erreur bloquante pour une fonctionnalité de
 * confort — même philosophie que {@code ConversationService#indexForRag}.
 */
@Service
public class DocumentReferenceContextService {

    private static final Logger log = LoggerFactory.getLogger(DocumentReferenceContextService.class);

    private final DocumentReferenceRepository documentReferenceRepository;
    private final SimilaritySearchService similaritySearchService;
    private final PromptBuilder promptBuilder;

    public DocumentReferenceContextService(DocumentReferenceRepository documentReferenceRepository,
                                            SimilaritySearchService similaritySearchService,
                                            PromptBuilder promptBuilder) {
        this.documentReferenceRepository = documentReferenceRepository;
        this.similaritySearchService = similaritySearchService;
        this.promptBuilder = promptBuilder;
    }

    public String enrich(UUID documentId, String userInstructions) {
        List<UUID> referenceIds = documentReferenceRepository.findByDocument_IdOrderByDateImportAsc(documentId).stream()
                .map(DocumentReference::getId)
                .toList();
        if (referenceIds.isEmpty()) {
            return userInstructions;
        }
        try {
            List<DocumentChunk> chunks = similaritySearchService.search(userInstructions, referenceIds);
            return promptBuilder.buildUserPrompt(userInstructions, chunks);
        } catch (Exception e) {
            log.warn("Enrichissement RAG impossible pour le document {} : {}", documentId, e.getMessage());
            return userInstructions;
        }
    }
}
