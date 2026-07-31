package com.docuai.ai.provider;

import com.docuai.ai.config.AiProperties;
import com.docuai.ai.enums.AiProvider;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Adaptateur DeepSeek — structuré (API compatible Chat Completions OpenAI,
 * {@code https://api.deepseek.com}, voir {@link AbstractOpenAiStyleAdapter}),
 * non branché par défaut : pas de {@code @Component}. Pour l'activer :
 * ajouter {@code @Component} et fournir {@code docuai.ai.deepseek.api-key}.
 */
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
