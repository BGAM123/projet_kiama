package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** Réponse de {@code POST /documents/{id}/sections/{sectionId}/improve} — suggestion IA, jamais appliquée automatiquement. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectionSuggestionDTO {
    private String aiSuggestedContent;
    /** Confiance auto-déclarée par le modèle (0-100) — absente si sa réponse n'a pas respecté le format attendu. */
    private Double confidenceScore;
}
