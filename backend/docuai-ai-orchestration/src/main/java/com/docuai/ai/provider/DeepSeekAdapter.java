package com.docuai.ai.provider;

import com.docuai.ai.config.AiProperties;
import com.docuai.ai.enums.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Adaptateur DeepSeek réel — API compatible Chat Completions OpenAI
 * ({@code https://api.deepseek.com}, voir {@link AbstractOpenAiStyleAdapter}).
 * Nécessite {@code docuai.ai.deepseek.api-key} pour être utilisable (sinon
 * {@link #requireApiKey()} refuse l'appel avec un message explicite).
 */
@Component
public class DeepSeekAdapter extends AbstractOpenAiStyleAdapter {

    private final AiProperties.DeepSeek properties;

    public DeepSeekAdapter(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        super(webClientBuilder.baseUrl(aiProperties.getDeepseek().getBaseUrl()).build());
        this.properties = aiProperties.getDeepseek();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.DEEPSEEK;
    }

    @Override
    protected String apiKey() {
        return properties.getApiKey();
    }

    @Override
    protected String defaultModel() {
        return "deepseek-chat";
    }

    @Override
    protected String missingApiKeyMessage() {
        return "DEEPSEEK_API_KEY absente — configurez docuai.ai.deepseek.api-key avant d'utiliser le fournisseur DeepSeek.";
    }
}
