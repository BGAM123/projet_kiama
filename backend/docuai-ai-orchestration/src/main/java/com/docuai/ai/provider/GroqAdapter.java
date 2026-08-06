package com.docuai.ai.provider;

import com.docuai.ai.config.AiProperties;
import com.docuai.ai.enums.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** Adaptateur Groq réel — API "OpenAI-compatible" (voir {@link AbstractOpenAiStyleAdapter}), inférence LPU à très faible latence. */
@Component
public class GroqAdapter extends AbstractOpenAiStyleAdapter {

    private final AiProperties.Groq properties;

    public GroqAdapter(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        super(webClientBuilder.baseUrl(aiProperties.getGroq().getBaseUrl()).build());
        this.properties = aiProperties.getGroq();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GROQ;
    }

    @Override
    protected String apiKey() {
        return properties.getApiKey();
    }

    @Override
    protected String defaultModel() {
        return "llama-3.3-70b-versatile";
    }

    @Override
    protected String missingApiKeyMessage() {
        return "GROQ_API_KEY absente — configurez docuai.ai.groq.api-key avant d'utiliser le fournisseur Groq.";
    }
}
