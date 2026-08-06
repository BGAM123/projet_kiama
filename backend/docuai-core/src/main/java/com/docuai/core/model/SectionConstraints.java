package com.docuai.core.model;

import lombok.*;

/**
 * Contraintes de format/longueur d'une section, portées par
 * {@link StructureNode#getConstraints()} (arbre JSONB
 * {@code document_structure.arbre_json}, définies à l'édition manuelle de la
 * structure) puis copiées telles quelles sur {@link GenerationSectionNode}
 * par {@code GenerationService.buildInitialSections} — validées par
 * {@code SectionConstraintValidator} (docuai-ai-orchestration) après chaque
 * génération de section, avec retry correctif en cas de violation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionConstraints {
    private Integer minLength;
    private Integer maxLength;
    /** Indice de forme attendue (ex. "markdown-table", "email", "date") — informatif, n'est pas vérifié par regex à lui seul. */
    private String format;
    /** Expression régulière que le contenu doit satisfaire (recherche partielle, pas une correspondance intégrale). */
    private String pattern;
}
