package com.docuai.core.model;

/**
 * Cycle de vie d'un document en édition manuelle assistée (contrainte
 * {@code chk_document_statut} de V9__document_manual_editing.sql,
 * V13__document_saved_status.sql pour {@link #SAUVEGARDE}). Remplace
 * {@link DocumentGenereStatut} (flux conversationnel de génération, retiré) —
 * plus de statut intermédiaire de génération/échec IA : la rédaction est
 * manuelle, section par section ({@link DocumentSectionStatut}), le document
 * lui-même passe par BROUILLON (squelette tout juste créé, jamais enregistré)
 * -> SAUVEGARDE (au moins un enregistrement effectué dans l'éditeur, cf.
 * {@code DocumentService#saveContent}) -> FINALISE (assemblé et exporté,
 * {@code POST /documents/{id}/finalize}) -> ARCHIVE.
 */
public enum DocumentStatut {
    BROUILLON,
    SAUVEGARDE,
    FINALISE,
    ARCHIVE
}
