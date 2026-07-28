package com.docuai.export.dto;

import com.docuai.export.enums.ExportFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {
    /**
     * Titre du document (sera utilisé comme nom de fichier potentiellement)
     */
    private String title;

    /**
     * Contenu du document à exporter (Texte brut, MD ou HTML nettoyé)
     */
    private String content;

    /**
     * Format cible
     */
    private ExportFormat format;
}
