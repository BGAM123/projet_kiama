package com.docuai.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Liaison de {@code docuai.ai.*} (application.yml / variables d'environnement,
 * cf. .env.example). Un sous-objet par fournisseur — {@code apiKey} vide
 * signifie "fournisseur non configuré" (voir les vérifications
 * {@code requireApiKey()} des adaptateurs {@code provider.*}).
 */
@ConfigurationProperties(prefix = "docuai.ai")
@Getter
public class AiProperties {

    private final OpenAi openai = new OpenAi();
    private final Anthropic anthropic = new Anthropic();
    private final Ollama ollama = new Ollama();
    private final Gemini gemini = new Gemini();
    private final Mistral mistral = new Mistral();
    private final DeepSeek deepseek = new DeepSeek();
    private final Groq groq = new Groq();
    private final Qwen qwen = new Qwen();

    @Getter
    @Setter
    public static class OpenAi {
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private String embeddingModel = "text-embedding-3-small";
    }

    @Getter
    @Setter
    public static class Anthropic {
        private String apiKey;
        private String baseUrl = "https://api.anthropic.com/v1";
        private String apiVersion = "2023-06-01";
    }

    @Getter
    @Setter
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
    }

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey;
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    }

    @Getter
    @Setter
    public static class Mistral {
        private String apiKey;
        private String baseUrl = "https://api.mistral.ai/v1";
    }

    @Getter
    @Setter
    public static class DeepSeek {
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com";
    }

    @Getter
    @Setter
    public static class Groq {
        private String apiKey;
        private String baseUrl = "https://api.groq.com/openai/v1";
    }

    /** DashScope (Alibaba Cloud) expose Qwen via un mode "compatible OpenAI" — même contrat que Mistral/DeepSeek/Groq. */
    @Getter
    @Setter
    public static class Qwen {
        private String apiKey;
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    }
}
