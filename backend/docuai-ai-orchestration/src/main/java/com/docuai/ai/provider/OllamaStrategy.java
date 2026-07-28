package com.docuai.ai.provider;

import com.docuai.ai.dto.AiRequest;
import com.docuai.ai.dto.AiResponse;
import com.docuai.ai.enums.AiProvider;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(name = "spring.ai.ollama.base-url")
public class OllamaStrategy implements ProviderStrategy {

    private final OllamaChatModel chatModel;

    public OllamaStrategy(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public AiProvider getSupportedProvider() {
        return AiProvider.OLLAMA;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        Prompt prompt = new prompt(request.getPrompt());
        
        ChatResponse response = chatModel.call(prompt);
        
        return AiResponse.builder()
                .content(response.getResult().getOutput().getContent())
                .usedProvider(AiProvider.OLLAMA)
                .generatedAt(LocalDateTime.now())
                .modelName(response.getMetadata().getModel())
                .build();
    }
}
