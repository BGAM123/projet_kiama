package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

/** Correspond exactement au type frontend {@code Conversation} (frontend/types/index.ts). */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationDTO {
    private UUID id;
    private UUID userId;
    private UUID documentTypeId;
    private String title;
    private String createdAt;
}
