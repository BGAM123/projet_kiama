package com.docuai.ai.provider;

import com.docuai.ai.config.AiProperties;
import com.docuai.ai.dto.ChatMessage;
import com.docuai.ai.dto.Chunk;
import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.dto.GenerationResult;
import com.docuai.ai.enums.AiProvider;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.port.AiProviderPort;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptateur Ollama réel (instance locale/self-hosted, {@code POST /api/chat}),
 * sans clé API — seule {@code docuai.ai.ollama.base-url} est nécessaire.
 * Streaming en NDJSON (une ligne JSON par delta, {@code message.content},
 * jusqu'à la ligne {@code done: true}) — pas de SSE, contrairement à
 * OpenAI/Anthropic/Gemini.
 */
@Component
public class OllamaAdapter implements AiProviderPort {

    private static final String DEFAULT_MODEL = "llama3";

    private final WebClient webClient;

    public OllamaAdapter(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        this.webClient = webClientBuilder.baseUrl(aiProperties.getOllama().getBaseUrl()).build();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OLLAMA;
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        Map<String, Object> body = buildBody(request, false);
        try {
            JsonNode response = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            String content = response.path("message").path("content").asText("");
            return GenerationResult.builder()
                    .content(content)
                    .provider(AiProvider.OLLAMA.name())
                    .model(String.valueOf(body.get("model")))
                    .promptTokens(response.path("prompt_eval_count").isMissingNode() ? null : response.path("prompt_eval_count").asInt())
                    .completionTokens(response.path("eval_count").isMissingNode() ? null : response.path("eval_count").asInt())
                    .build();
        } catch (Exception e) {
            throw new AiProviderException("Échec de l'appel à Ollama — instance locale démarrée ? Voir docuai.ai.ollama.base-url.", e);
        }
    }

    @Override
    public Flux<Chunk> streamGenerate(GenerationRequest request) {
        Map<String, Object> body = buildBody(request, true);
        return webClient.post()
                .uri("/api/chat")
                .bodyValue(body)
                .retrieve()
                // JsonNode plutôt que String : le Content-Type NDJSON d'Ollama
                // (application/x-ndjson) n'indique pas de charset, et
                // StringDecoder retombait sur ISO-8859-1 pour le décoder,
                // corrompant les accents (ex. "é" -> "Ã©"). Jackson lit les
                // octets bruts en UTF-8 (imposé par la RFC JSON), comme le
                // fait déjà generate() via bodyToMono(JsonNode.class).
                .bodyToFlux(JsonNode.class)
                .mapNotNull(this::extractContent)
                .filter(text -> text != null && !text.isEmpty())
                .map(text -> Chunk.builder().content(text).last(false).build())
                .onErrorMap(e -> e instanceof AiProviderException ? e : new AiProviderException("Échec du streaming Ollama.", e));
    }

    private String extractContent(JsonNode node) {
        return node.path("message").path("content").asText("");
    }

    private Map<String, Object> buildBody(GenerationRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel() != null && !request.getModel().isBlank() ? request.getModel() : DEFAULT_MODEL);
        body.put("messages", toMessages(request));
        body.put("stream", stream);
        return body;
    }

    private List<Map<String, String>> toMessages(GenerationRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        for (ChatMessage m : request.getHistory()) {
            messages.add(Map.of("role", "ASSISTANT".equalsIgnoreCase(m.getRole()) ? "assistant" : "user", "content", m.getContent()));
        }
        messages.add(Map.of("role", "user", "content", request.getUserPrompt()));
        return messages;
    }
}
