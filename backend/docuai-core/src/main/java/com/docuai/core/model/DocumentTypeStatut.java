package com.docuai.core.model;

/**
 * Cycle de vie d'un Document Type (section 4.6 / contrainte {@code
 * chk_document_type_statut} de V1__init_schema.sql). Les transitions
 * IMPORTE -> EN_EXTRACTION -> STRUCTURE_EXTRAITE|ECHEC_EXTRACTION ->
 * EN_VALIDATION -> ACTIF -> ARCHIVE sont pilotées par le pipeline
 * d'extraction (Bloc 4) et la validation manuelle, pas par le CRUD générique
 * de {@code DocumentTypeService#update} (Bloc 3, référentiels).
 */
public enum DocumentTypeStatut {
    IMPORTE,
    EN_EXTRACTION,
    STRUCTURE_EXTRAITE,
    EN_VALIDATION,
    ACTIF,
    ARCHIVE,
    ECHEC_EXTRACTION
}
