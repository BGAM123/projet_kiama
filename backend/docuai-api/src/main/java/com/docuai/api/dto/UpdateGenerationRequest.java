package com.docuai.api.dto;

import lombok.Data;

/** Édition manuelle du contenu d'un document généré ({@code DOCUMENT_EDIT_OWN}). */
@Data
public class UpdateGenerationRequest {
    private String content;
}
