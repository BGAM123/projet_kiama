package com.docuai.api.dto;

import lombok.Data;

import java.util.UUID;

/**
 * CRUD référentiel uniquement (nom/description/catégorie). Le statut et la
 * version évoluent via le pipeline d'extraction (Bloc 4) et la génération de
 * nouvelles versions, pas via ce endpoint générique.
 */
@Data
public class UpdateDocumentTypeRequest {
    private String name;
    private String description;
    private UUID categoryId;
}
