package com.docuai.ai.rag;

import com.docuai.core.model.DocumentChunk;
import com.docuai.core.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Recherche par similarité cosinus (pgvector, opérateur {@code <=>}) parmi les
 * chunks des documents de référence attachés à une conversation (Bloc 6), pour
 * alimenter {@code PromptBuilder#buildUserPrompt} avec le contexte pertinent.
 */
@Service
public class SimilaritySearchService {

    private static final int DEFAULT_TOP_K = 5;

    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;

    public SimilaritySearchService(DocumentChunkRepository documentChunkRepository, EmbeddingService embeddingService) {
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
    }

    public List<DocumentChunk> search(String query, List<UUID> referenceIds) {
        return search(query, referenceIds, DEFAULT_TOP_K);
    }

    public List<DocumentChunk> search(String query, List<UUID> referenceIds, int topK) {
        if (referenceIds == null || referenceIds.isEmpty()) {
            return List.of();
        }
        float[] queryEmbedding = embeddingService.embed(query);
        return documentChunkRepository.findNearest(referenceIds, toLiteral(queryEmbedding), topK);
    }

    private String toLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
