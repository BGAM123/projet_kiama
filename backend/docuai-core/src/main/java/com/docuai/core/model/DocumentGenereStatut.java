package com.docuai.core.model;

/**
 * Cycle de vie d'un document généré (contrainte {@code chk_document_genere_statut}
 * de V1__init_schema.sql). BROUILLON -> EN_GENERATION -> GENERE|ECHEC ->
 * EN_EDITION (édition manuelle, {@code DOCUMENT_EDIT_OWN}) -> EXPORTE (Bloc 7)
 * -> ARCHIVE.
 */
public enum DocumentGenereStatut {
    BROUILLON,
    EN_GENERATION,
    GENERE,
    ECHEC,
    EN_EDITION,
    EXPORTE,
    ARCHIVE
}
