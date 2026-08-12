package com.docuai.core.model;

/**
 * Type d'une section de document en édition manuelle (contrainte {@code
 * chk_document_section_type} de V9__document_manual_editing.sql). Vocabulaire
 * propre à ce nouveau modèle relationnel — distinct de {@code
 * StructureNode#getType()} (arbre JSONB du Document Type, resté en {@code
 * heading}/{@code level} pour ne pas casser l'extraction déterministe, le
 * rendu de prompt et l'éditeur de structure existants). {@code
 * DocumentService} traduit l'un vers l'autre à la création d'un document
 * (aplatissement de l'arbre en lignes {@link DocumentSection}).
 */
public enum DocumentSectionType {
    TITLE,
    SUBTITLE,
    SUB_SUBTITLE,
    TABLE,
    PARAGRAPH_PLACEHOLDER
}
