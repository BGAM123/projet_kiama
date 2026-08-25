package com.docuai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Corps de {@code POST /documents/{id}/generate-at-cursor} : instruction libre du composer IA de l'éditeur. */
@Data
public class GenerateAtCursorRequest {
    @NotBlank(message = "Décrivez ce que vous souhaitez générer.")
    private String instruction;
}
