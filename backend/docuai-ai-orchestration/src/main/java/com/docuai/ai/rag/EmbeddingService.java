package com.docuai.ai.rag;

import com.docuai.ai.config.AiProperties;
import com.docuai.ai.exception.AiProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calcule les embeddings vectoriels (OpenAI {@code text-embedding-3-small},
 * 1536 dimensions — cf. commentaire de la colonne {@code document_chunk.embedding}
 * dans V1__init_schema.sql) utilisés par {@link ChunkingService} (indexation)
 * et {@code SimilaritySearchService} (requête). Fournisseur unique
 * volontairement : mélanger des embeddings de modèles différents dans le même
 * espace vectoriel pgvector produirait des similarités incohérentes.
 */
@Service
public class EmbeddingService {

    private final WebClient webClient;
    private final AiProperties.OpenAi properties;

    public EmbeddingService(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        this.properties = aiProperties.getOpenai();
        this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    public List<float[]> embedAll(List<String> texts) {
        requireApiKey();
        try {
            JsonNode response = webClient.post()
                    .uri("/embeddings")
                    .headers(h -> h.setBearerAuth(properties.getApiKey()))
                    .bodyValue(Map.of("model", properties.getEmbeddingModel(), "input", texts))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            List<float[]> result = new ArrayList<>();
            for (JsonNode item : response.path("data")) {
                JsonNode vector = item.path("embedding");
                float[] values = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) {
                    values[i] = (float) vector.get(i).asDouble();
                }
                result.add(values);
            }
            return result;
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Échec du calcul des embeddings (OpenAI).", e);
        }
    }

    private void requireApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiProviderException("OPENAI_API_KEY absente — impossible de calculer des embeddings (RAG). Configurez docuai.ai.openai.api-key.");
        }
    }
}
