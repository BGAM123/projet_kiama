package com.docuai.ai.dto;

import lombok.Builder;
import lombok.Getter;

/** Message d'historique de conversation passé à un {@link com.docuai.ai.port.AiProviderPort} (role : USER|ASSISTANT|SYSTEM, cf. contrainte {@code chk_message_role}). */
@Getter
@Builder
public class ChatMessage {
    private final String role;
    private final String content;
}
