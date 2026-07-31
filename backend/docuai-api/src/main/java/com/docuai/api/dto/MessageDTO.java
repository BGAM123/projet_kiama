package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

/**
 * Correspond exactement au type frontend {@code Message}. {@code role} est
 * en minuscules ("user"/"assistant") côté frontend, alors que
 * {@code message.role} en base et l'énum Java {@code MessageRole} sont en
 * majuscules — conversion faite explicitement dans {@code ConversationMapper}
 * (pas de mapping SYSTEM : ces messages ne sont pas exposés au frontend).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageDTO {
    private UUID id;
    private UUID conversationId;
    private String role;
    private String content;
    private String createdAt;
}
