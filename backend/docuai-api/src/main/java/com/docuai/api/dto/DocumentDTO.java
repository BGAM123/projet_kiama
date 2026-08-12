package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Correspond exactement au type frontend {@code Document}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentDTO {
    private UUID id;
    private UUID documentTypeId;
    private UUID userId;
    private String status;
    private String language;
    private String tone;
    private List<DocumentSectionDTO> sections;
    /** Moyenne des scores de confiance des sections évaluées (0-100, arrondie) — absente tant qu'aucune section n'a de score. */
    private Integer globalConfidenceScore;
    private String createdAt;
    private String updatedAt;
    /** URL de téléchargement pré-signée (MinIO) — absente tant que le document n'a pas été finalisé. */
    private String exportUrl;
}
