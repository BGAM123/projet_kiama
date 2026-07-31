package com.docuai.api.dto;

import com.docuai.core.model.Language;
import com.docuai.core.model.TargetLength;
import com.docuai.core.model.Tone;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Correspond exactement au contrat frontend {@code startGeneration} (frontend/lib/api/client.ts). */
@Data
public class StartGenerationRequest {
    @NotNull
    private UUID conversationId;
    @NotNull
    private UUID documentTypeId;
    @NotNull
    private UUID userId;
    @NotNull
    private Language language;
    @NotNull
    private Tone tone;
    private TargetLength targetLength;
    private String contentPivot;
}
