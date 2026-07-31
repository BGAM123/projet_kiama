package com.docuai.core.model;

import lombok.*;

/**
 * Élément de la valeur JSONB {@code document_genere.sections} (pas une entité
 * JPA — même principe que {@link StructureNode} pour {@code document_structure.arbre_json}).
 * Les noms de champs correspondent exactement au type frontend
 * {@code GenerationSection} (frontend/types/index.ts). {@code status} reste
 * un {@code String} libre ("PENDING"/"GENERATING"/"DONE"/"FAILED") plutôt
 * qu'un énum Java : cette valeur ne vit que dans le JSON, sans contrainte
 * CHECK en base.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerationSectionNode {
    private String id;
    private String label;
    private String status;
    private String content;
}
