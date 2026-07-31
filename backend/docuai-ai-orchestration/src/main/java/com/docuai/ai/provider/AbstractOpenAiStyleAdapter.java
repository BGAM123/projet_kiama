package com.docuai.ai.provider;

import com.docuai.ai.dto.ChatMessage;
import com.docuai.ai.dto.Chunk;
import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.dto.GenerationResult;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.port.AiProviderPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base commune aux fournisseurs exposant une API "Chat Completions"
 * compatible OpenAI ({@code POST /chat/completions}, streaming SSE via
 * {@code choices[0].delta.content} jusqu'à {@code data: [DONE]}) : OpenAI
 * lui-même, ainsi que Mistral et DeepSeek qui documentent explicitement cette
 * compatibilité. Évite de tripler la construction de requête et le parsing de
 * réponse entre les trois adaptateurs.
 */
abstract class AbstractOpenAiStyleAdapter implements AiProviderPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected final WebClient webClient;

    protected AbstractOpenAiStyleAdapter(WebClient webClient) {
        this.webClient = webClient;
    }

    protected abstract String apiKey();

    protected abstract String defaultModel();

    protected abstract String missingApiKeyMessage();

    @Override
    public GenerationResult generate(GenerationRequest request) {
        requireApiKey();
        Map<String, Object> body = buildBody(request, false);
        try {
            JsonNode response = webClient.post()
                    .uri("/chat/completions")
                    .headers(h -> h.setBearerAuth(apiKey()))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            JsonNode choice = response.path("choices").get(0);
            String content = choice.path("message").path("content").asText("");
            JsonNode usage = response.path("usage");
            return GenerationResult.builder()
                    .content(content)
                    .provider(provider().name())
                    .model(String.valueOf(body.get("model")))
                    .promptTokens(usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt())
                    .completionTokens(usage.path("completion_tokens").isMissingNode() ? null : usage.path("completion_tokens").asInt())
                    .build();
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Échec de l'appel à " + provider() + " (chat completions).", e);
        }
    }

    @Override
    public Flux<Chunk> streamGenerate(GenerationRequest request) {
        requireApiKey();
        Map<String, Object> body = buildBody(request, true);
        return webClient.post()
                .uri("/chat/completions")
                .headers(h -> h.setBearerAuth(apiKey()))
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .mapNotNull(ServerSentEvent::data)
                .filter(data -> !"[DONE]".equals(data))
                .mapNotNull(this::extractDelta)
                .filter(text -> text != null && !text.isEmpty())
                .map(text -> Chunk.builder().content(text).last(false).build())
                .onErrorMap(e -> e instanceof AiProviderException ? e : new AiProviderException("Échec du streaming " + provider() + ".", e));
    }

    private String extractDelta(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            return node.path("choices").get(0).path("delta").path("content").asText("");
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildBody(GenerationRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel() != null && !request.getModel().isBlank() ? request.getModel() : defaultModel());
        body.put("messages", toMessages(request));
        body.put("stream", stream);
        if (request.getTemperature() != null) body.put("temperature", request.getTemperature());
        if (request.getMaxOutputTokens() != null) body.put("max_tokens", request.getMaxOutputTokens());
        return body;
    }

    private List<Map<String, String>> toMessages(GenerationRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        for (ChatMessage m : request.getHistory()) {
            messages.add(Map.of("role", mapRole(m.getRole()), "content", m.getContent()));
        }
        messages.add(Map.of("role", "user", "content", request.getUserPrompt()));
        return messages;
    }

    private String mapRole(String role) {
        return switch (role == null ? "" : role.toUpperCase()) {
            case "ASSISTANT" -> "assistant";
            case "SYSTEM" -> "system";
            default -> "user";
        };
    }

    private void requireApiKey() {
        if (apiKey() == null || apiKey().isBlank()) {
            throw new AiProviderException(missingApiKeyMessage());
        }
    }
}
