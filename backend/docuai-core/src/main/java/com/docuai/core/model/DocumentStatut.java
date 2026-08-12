package com.docuai.core.model;

/**
 * Cycle de vie d'un document en édition manuelle assistée (contrainte
 * {@code chk_document_statut} de V9__document_manual_editing.sql). Remplace
 * {@link DocumentGenereStatut} (flux conversationnel de génération, retiré) —
 * plus de statut intermédiaire de génération/échec IA : la rédaction est
 * manuelle, section par section ({@link DocumentSectionStatut}), le document
 * lui-même n'a que trois états : BROUILLON (en cours de rédaction) ->
 * FINALISE (assemblé et exporté, {@code POST /documents/{id}/finalize}) ->
 * ARCHIVE.
 */
public enum DocumentStatut {
    BROUILLON,
    FINALISE,
    ARCHIVE
}
