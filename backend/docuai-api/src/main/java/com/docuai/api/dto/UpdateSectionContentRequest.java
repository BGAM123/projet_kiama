package com.docuai.api.dto;

import lombok.Data;

/** Corps de {@code PUT /documents/{id}/sections/{sectionId}} : sauvegarde du contenu rédigé par l'utilisateur (autosave). */
@Data
public class UpdateSectionContentRequest {
    private String content;
}
