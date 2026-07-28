package com.docuai.extraction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedContentDetail {
    /**
     * Nom du fichier d'origine
     */
    private String fileName;
    
    /**
     * Type MIME détecté (ex: application/pdf)
     */
    private String mimeType;
    
    /**
     * Le texte brut extrait par l'outil de parsing (Tika / PDFBox)
     */
    private String rawText;
    
    /**
     * URL du fichier sur MinIO / S3
     */
    private String fileUrl;
}
