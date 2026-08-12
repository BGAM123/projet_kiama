package com.docuai.core.model;

/**
 * Cycle de vie d'une section de document (contrainte {@code
 * chk_document_section_statut}) : EMPTY (pas encore rédigée) -> DRAFTED
 * (l'utilisateur a écrit) -> AI_IMPROVED (une suggestion IA a été appliquée
 * comme nouveau contenu, {@code POST .../apply-suggestion}) -> FINALIZED.
 * Une section peut revenir de AI_IMPROVED à DRAFTED si l'utilisateur réédite
 * manuellement après application d'une suggestion (voir {@code
 * DocumentSectionService#saveContent}).
 */
public enum DocumentSectionStatut {
    EMPTY,
    DRAFTED,
    AI_IMPROVED,
    FINALIZED
}
