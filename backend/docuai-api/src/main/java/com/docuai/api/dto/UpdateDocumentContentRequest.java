package com.docuai.api.dto;

import lombok.Data;

/**
 * Corps de {@code PUT /documents/{id}/content} : le document complet tel que
 * mis en forme dans l'éditeur type Word. Pas de contrainte de taille : les
 * images insérées sont encodées dans le HTML lui-même, un document illustré
 * pèse légitimement plusieurs Mo (voir {@code spring.servlet.multipart} et la
 * limite de taille de requête côté configuration).
 */
@Data
public class UpdateDocumentContentRequest {
    private String contentHtml;
}
