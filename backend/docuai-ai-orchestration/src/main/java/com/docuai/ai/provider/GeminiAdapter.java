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
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptateur Gemini réel — Generative Language API,
 * {@code models/{model}:generateContent} / {@code :streamGenerateContent?alt=sse}.
 * Format de requête différent d'OpenAI/Claude ({@code contents[].parts[].text},
 * rôles "user"/"model", clé API en paramètre de requête {@code ?key=}) — pas
 * de mutualisation possible avec {@link AbstractOpenAiStyleAdapter}.
 * Nécessite {@code docuai.ai.gemini.api-key} pour être utilisable (sinon
 * {@link #requireApiKey()} refuse l'appel avec un message explicite).
 */
@Component
public class GeminiAdapter implements AiProviderPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_MODEL = "gemini-1.5-flash";

    private final WebClient webClient;
    private final AiProperties.Gemini properties;

    public GeminiAdapter(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        this.properties = aiProperties.getGemini();
        this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GEMINI;
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        String effectiveKey = resolveApiKey(request);
        requireApiKey(effectiveKey);
        String model = modelOf(request);
        Map<String, Object> body = buildBody(request);
        try {
            JsonNode response = webClient.post()
                    .uri(uri -> uri.path("/models/{model}:generateContent")
                            .queryParam("key", effectiveKey)
                            .build(model))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            String content = response.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText("");
            JsonNode usage = response.path("usageMetadata");
            return GenerationResult.builder()
                    .content(content)
                    .provider(AiProvider.GEMINI.name())
                    .model(model)
                    .promptTokens(usage.path("promptTokenCount").isMissingNode() ? null : usage.path("promptTokenCount").asInt())
                    .completionTokens(usage.path("candidatesTokenCount").isMissingNode() ? null : usage.path("candidatesTokenCount").asInt())
                    .build();
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Échec de l'appel à Gemini (Generative Language API).", e);
        }
    }

    @Override
    public Flux<Chunk> streamGenerate(GenerationRequest request) {
        String effectiveKey = resolveApiKey(request);
        requireApiKey(effectiveKey);
        String model = modelOf(request);
        Map<String, Object> body = buildBody(request);
        return webClient.post()
                .uri(uri -> uri.path("/models/{model}:streamGenerateContent")
                        .queryParam("alt", "sse")
                        .queryParam("key", effectiveKey)
                        .build(model))
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .mapNotNull(ServerSentEvent::data)
                .mapNotNull(this::extractDelta)
                .filter(text -> text != null && !text.isEmpty())
                .map(text -> Chunk.builder().content(text).last(false).build())
                .onErrorMap(e -> e instanceof AiProviderException ? e : new AiProviderException("Échec du streaming Gemini.", e));
    }

    private String extractDelta(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            return node.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText("");
        } catch (Exception e) {
            return null;
        }
    }

    private String modelOf(GenerationRequest request) {
        return request.getModel() != null && !request.getModel().isBlank() ? request.getModel() : DEFAULT_MODEL;
    }

    private Map<String, Object> buildBody(GenerationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.getSystemPrompt()))));
        }
        body.put("contents", toContents(request));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        if (request.getTemperature() != null) generationConfig.put("temperature", request.getTemperature());
        if (request.getMaxOutputTokens() != null) generationConfig.put("maxOutputTokens", request.getMaxOutputTokens());
        if (!generationConfig.isEmpty()) body.put("generationConfig", generationConfig);
        return body;
    }

    private List<Map<String, Object>> toContents(GenerationRequest request) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatMessage m : request.getHistory()) {
            String role = "ASSISTANT".equalsIgnoreCase(m.getRole()) ? "model" : "user";
            contents.add(Map.of("role", role, "parts", List.of(Map.of("text", m.getContent()))));
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", request.getUserPrompt()))));
        return contents;
    }

    private String resolveApiKey(GenerationRequest request) {
        String override = request.getApiKeyOverride();
        return (override != null && !override.isBlank()) ? override : properties.getApiKey();
    }

    private void requireApiKey(String effectiveKey) {
        if (effectiveKey == null || effectiveKey.isBlank()) {
            throw new AiProviderException("GEMINI_API_KEY absente — configurez docuai.ai.gemini.api-key avant d'utiliser le fournisseur Gemini.");
        }
    }
}
