package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Correspond exactement au type frontend {@code DocumentSection}. Liste à plat (le frontend reconstruit l'arbre via {@code parentSectionId}), comme les lignes de {@code document_section}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentSectionDTO {
    private UUID id;
    private UUID parentSectionId;
    private String type;
    private Integer level;
    private String label;
    private List<TableColumnDefDTO> tableColumns;
    private Integer order;
    private String userContent;
    private String aiSuggestedContent;
    /** Confiance (0-100) de la dernière sortie IA sur cette section — absente tant qu'aucune amélioration n'a été produite ou retenue. */
    private Double confidenceScore;
    private String status;
}
