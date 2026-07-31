package com.docuai.ai.dto;

import lombok.Builder;
import lombok.Getter;

/** Fragment de texte incrémental émis par {@link com.docuai.ai.port.AiProviderPort#streamGenerate} — un élément par delta reçu du fournisseur (SSE OpenAI/Anthropic/Gemini, NDJSON Ollama). */
@Getter
@Builder
public class Chunk {
    private final String content;
    private final boolean last;
}
