package com.docuai.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateConversationRequest {
    @NotNull
    private UUID userId;
    @NotNull
    private UUID documentTypeId;
    private String title;
}
