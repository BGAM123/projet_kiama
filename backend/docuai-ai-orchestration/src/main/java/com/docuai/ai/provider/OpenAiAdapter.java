package com.docuai.ai.provider;

import com.docuai.ai.config.AiProperties;
import com.docuai.ai.enums.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** Adaptateur OpenAI réel — Chat Completions API (voir {@link AbstractOpenAiStyleAdapter}). */
@Component
public class OpenAiAdapter extends AbstractOpenAiStyleAdapter {

    private final AiProperties.OpenAi properties;

    public OpenAiAdapter(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        super(webClientBuilder.baseUrl(aiProperties.getOpenai().getBaseUrl()).build());
        this.properties = aiProperties.getOpenai();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OPENAI;
    }

    @Override
    protected String apiKey() {
        return properties.getApiKey();
    }

    @Override
    protected String defaultModel() {
        return "gpt-4o-mini";
    }

    @Override
    protected String missingApiKeyMessage() {
        return "OPENAI_API_KEY absente — configurez docuai.ai.openai.api-key avant d'utiliser le fournisseur OpenAI.";
    }
}
