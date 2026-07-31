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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptateur Claude réel (Anthropic Messages API, {@code POST /messages}), y
 * compris streaming SSE ({@code event: content_block_delta}, {@code delta.text}).
 * Contrairement à OpenAI, le prompt système est un champ top-level
 * {@code system}, pas un message de rôle "system" dans le tableau
 * {@code messages} ; l'authentification passe par l'en-tête {@code x-api-key}
 * (pas {@code Authorization: Bearer}).
 */
@Component
public class ClaudeAdapter implements AiProviderPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_MODEL = "claude-3-5-haiku-latest";

    private final WebClient webClient;
    private final AiProperties.Anthropic properties;

    public ClaudeAdapter(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        this.properties = aiProperties.getAnthropic();
        this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.CLAUDE;
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        requireApiKey();
        Map<String, Object> body = buildBody(request, false);
        try {
            JsonNode response = webClient.post()
                    .uri("/messages")
                    .headers(this::authHeaders)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            StringBuilder content = new StringBuilder();
            for (JsonNode block : response.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    content.append(block.path("text").asText(""));
                }
            }
            JsonNode usage = response.path("usage");
            return GenerationResult.builder()
                    .content(content.toString())
                    .provider(AiProvider.CLAUDE.name())
                    .model(String.valueOf(body.get("model")))
                    .promptTokens(usage.path("input_tokens").isMissingNode() ? null : usage.path("input_tokens").asInt())
                    .completionTokens(usage.path("output_tokens").isMissingNode() ? null : usage.path("output_tokens").asInt())
                    .build();
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Échec de l'appel à Claude (Anthropic Messages API).", e);
        }
    }

    @Override
    public Flux<Chunk> streamGenerate(GenerationRequest request) {
        requireApiKey();
        Map<String, Object> body = buildBody(request, true);
        return webClient.post()
                .uri("/messages")
                .headers(this::authHeaders)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .filter(event -> "content_block_delta".equals(event.event()))
                .mapNotNull(ServerSentEvent::data)
                .mapNotNull(this::extractDelta)
                .filter(text -> text != null && !text.isEmpty())
                .map(text -> Chunk.builder().content(text).last(false).build())
                .onErrorMap(e -> e instanceof AiProviderException ? e : new AiProviderException("Échec du streaming Claude.", e));
    }

    private String extractDelta(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            JsonNode delta = node.path("delta");
            if ("text_delta".equals(delta.path("type").asText())) {
                return delta.path("text").asText("");
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void authHeaders(HttpHeaders headers) {
        headers.set("x-api-key", properties.getApiKey());
        headers.set("anthropic-version", properties.getApiVersion());
    }

    private Map<String, Object> buildBody(GenerationRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel() != null && !request.getModel().isBlank() ? request.getModel() : DEFAULT_MODEL);
        body.put("max_tokens", request.getMaxOutputTokens() != null ? request.getMaxOutputTokens() : 4096);
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("system", request.getSystemPrompt());
        }
        body.put("messages", toMessages(request));
        body.put("stream", stream);
        if (request.getTemperature() != null) body.put("temperature", request.getTemperature());
        return body;
    }

    private List<Map<String, String>> toMessages(GenerationRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage m : request.getHistory()) {
            messages.add(Map.of("role", "ASSISTANT".equalsIgnoreCase(m.getRole()) ? "assistant" : "user", "content", m.getContent()));
        }
        messages.add(Map.of("role", "user", "content", request.getUserPrompt()));
        return messages;
    }

    private void requireApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiProviderException("ANTHROPIC_API_KEY absente — configurez docuai.ai.anthropic.api-key avant d'utiliser le fournisseur Claude.");
        }
    }
}
