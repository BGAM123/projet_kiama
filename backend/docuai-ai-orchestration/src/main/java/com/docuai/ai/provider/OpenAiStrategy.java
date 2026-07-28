package com.docuai.ai.provider;

import com.docuai.ai.dto.AiRequest;
import com.docuai.ai.dto.AiResponse;
import com.docuai.ai.enums.AiProvider;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(name = "spring.ai.openai.api-key")
public class OpenAiStrategy implements ProviderStrategy {

    private final OpenAiChatModel chatModel;

    public OpenAiStrategy(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public AiProvider getSupportedProvider() {
        return AiProvider.OPENAI;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        // Construction très basique du prompt pour commencer
        Prompt prompt = new Prompt(request.getPrompt());
        
        ChatResponse response = chatModel.call(prompt);
        
        return AiResponse.builder()
                .content(response.getResult().getOutput().getContent())
                .usedProvider(AiProvider.OPENAI)
                .generatedAt(LocalDateTime.now())
                .modelName(response.getMetadata().getModel())
                .build();
    }
}
