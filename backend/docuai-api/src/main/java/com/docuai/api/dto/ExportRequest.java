package com.docuai.api.dto;

import com.docuai.export.ExportFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExportRequest {
    private String title;

    @NotBlank
    private String content;

    @NotNull
    private ExportFormat format;

    /** Texte statique extrait du Document Type source (voir DocumentStructure.headerText/footerText) — jamais généré par l'IA. */
    private String headerText;
    private String footerText;
}
