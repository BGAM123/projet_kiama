package com.docuai.ai.provider;

import com.docuai.ai.config.AiProperties;
import com.docuai.ai.enums.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Adaptateur Qwen (Alibaba Cloud) réel — DashScope expose un mode
 * "compatible OpenAI" pour Qwen (voir {@link AbstractOpenAiStyleAdapter}),
 * pas de format de requête spécifique à gérer contrairement à Gemini.
 * Nécessite {@code docuai.ai.qwen.api-key} pour être utilisable (sinon
 * {@link #requireApiKey()} refuse l'appel avec un message explicite).
 */
@Component
public class QwenAdapter extends AbstractOpenAiStyleAdapter {

    private final AiProperties.Qwen properties;

    public QwenAdapter(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        super(webClientBuilder.baseUrl(aiProperties.getQwen().getBaseUrl()).build());
        this.properties = aiProperties.getQwen();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.QWEN;
    }

    @Override
    protected String apiKey() {
        return properties.getApiKey();
    }

    @Override
    protected String defaultModel() {
        return "qwen-plus";
    }

    @Override
    protected String missingApiKeyMessage() {
        return "QWEN_API_KEY absente — configurez docuai.ai.qwen.api-key avant d'utiliser le fournisseur Qwen.";
    }
}
