package com.docuai.ai.rag;

import com.docuai.core.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Découpe le texte brut d'un document de référence (Tika, cf.
 * docuai-extraction-client) en fragments de taille bornée avant embedding
 * ({@link EmbeddingService}) et stockage ({@code document_chunk}, pgvector).
 * Découpage simple par nombre de caractères avec chevauchement — pas de
 * découpage sémantique (par phrase/paragraphe) pour cette itération, même
 * limitation assumée que pour l'extraction de structure au Bloc 4.
 */
@Service
public class ChunkingService {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_OVERLAP = 150;

    public List<DocumentChunk> chunk(UUID referenceId, String rawText) {
        return chunk(referenceId, rawText, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<DocumentChunk> chunk(UUID referenceId, String rawText, int chunkSize, int overlap) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return chunks;
        }
        String normalized = rawText.strip();
        int index = 0;
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            String content = normalized.substring(start, end).strip();
            if (!content.isEmpty()) {
                chunks.add(DocumentChunk.builder()
                        .idReference(referenceId)
                        .indexChunk(index++)
                        .contenu(content)
                        .build());
            }
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(0, end - overlap);
        }
        return chunks;
    }
}
