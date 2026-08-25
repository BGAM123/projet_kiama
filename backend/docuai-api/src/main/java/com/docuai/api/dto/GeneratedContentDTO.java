package com.docuai.api.dto;

import lombok.Data;

/** HTML prêt à insérer tel quel dans l'éditeur (déjà échappé/formaté côté serveur, cf. DocumentService#generateAtCursor). */
@Data
public class GeneratedContentDTO {
    private String contentHtml;
}
