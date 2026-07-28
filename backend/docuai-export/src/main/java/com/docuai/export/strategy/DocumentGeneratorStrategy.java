package com.docuai.export.strategy;

import com.docuai.export.dto.ExportRequest;
import com.docuai.export.enums.ExportFormat;

public interface DocumentGeneratorStrategy {

    /**
     * Le format pris en charge par ce générateur.
     */
    ExportFormat getSupportedFormat();

    /**
     * Génère un fichier à partir du contenu.
     * @param request Le contenu et les métadonnées de la demande
     * @return Les octets du document généré
     */
    byte[] generateDocument(ExportRequest request);

    /**
     * Fournit l'extension de fichier standard.
     */
    String getFileExtension();
    
    /**
     * Le type mime correspondant au fichier produit.
     */
    String getContentType();
}
