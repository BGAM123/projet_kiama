package com.docuai.ai.provider;

import com.docuai.ai.config.AiProperties;
import com.docuai.ai.enums.AiProvider;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Adaptateur Mistral — structuré (API "La Plateforme" compatible Chat
 * Completions OpenAI, voir {@link AbstractOpenAiStyleAdapter}), non branché
 * par défaut : pas de {@code @Component}, donc non auto-découvert par
 * {@code AiProviderFactory}. Pour l'activer : ajouter {@code @Component} et
 * fournir {@code docuai.ai.mistral.api-key} (voir README du module).
 */
public class MistralAdapter extends AbstractOpenAiStyleAdapter {

    private final AiProperties.Mistral properties;

    public MistralAdapter(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        super(webClientBuilder.baseUrl(aiProperties.getMistral().getBaseUrl()).build());
        this.properties = aiProperties.getMistral();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.MISTRAL;
    }

    @Override
    protected String apiKey() {
        return properties.getApiKey();
    }

    @Override
    protected String defaultModel() {
        return "mistral-small-latest";
    }

    @Override
    protected String missingApiKeyMessage() {
        return "MISTRAL_API_KEY absente — configurez docuai.ai.mistral.api-key avant d'utiliser le fournisseur Mistral.";
    }
}
