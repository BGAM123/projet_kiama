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
    /** Absent pour un document importé directement depuis un fichier (pas de gabarit) — voir {@link #title}. */
    private UUID documentTypeId;
    private UUID userId;
    /** Titre du document. Toujours résolu par le serveur : nom du Document Type, ou nom du fichier importé. */
    private String title;
    private String status;
    private String language;
    private String tone;
    /** Plan hérité du Document Type — sert de sommaire et de support à l'amélioration IA ; le contenu rédigé vit dans {@link #contentHtml}. */
    private List<DocumentSectionDTO> sections;
    /** Document complet mis en forme dans l'éditeur type Word. Absent pour les documents antérieurs à cet éditeur. */
    private String contentHtml;
    /** Moyenne des scores de confiance des sections évaluées (0-100, arrondie) — absente tant qu'aucune section n'a de score. */
    private Integer globalConfidenceScore;
    private String createdAt;
    private String updatedAt;
    /** URL de téléchargement pré-signée (MinIO) — absente tant que le document n'a pas été finalisé. */
    private String exportUrl;
}
