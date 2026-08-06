package com.docuai.ai.provider;

import com.docuai.ai.config.AiProperties;
import com.docuai.ai.enums.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Adaptateur Mistral — API "La Plateforme" compatible Chat Completions OpenAI
 * (voir {@link AbstractOpenAiStyleAdapter}). Nécessite
 * {@code docuai.ai.mistral.api-key} pour être utilisable (sinon
 * {@link #requireApiKey()} refuse l'appel avec un message explicite).
 */
@Component
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
