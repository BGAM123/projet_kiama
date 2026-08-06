package com.docuai.core.model;

import lombok.*;

import java.util.List;

/**
 * Élément de la valeur JSONB {@code document_genere.sections} (pas une entité
 * JPA — même principe que {@link StructureNode} pour {@code document_structure.arbre_json}).
 * Les noms de champs correspondent exactement au type frontend
 * {@code GenerationSection} (frontend/types/index.ts). {@code status} reste
 * un {@code String} libre ("PENDING"/"GENERATING"/"DONE"/"FAILED") plutôt
 * qu'un énum Java : cette valeur ne vit que dans le JSON, sans contrainte
 * CHECK en base.
 * <p>
 * {@code type}/{@code level}/{@code columns}/{@code required}/
 * {@code constraints} reprennent tels quels les champs de même nom sur
 * {@link StructureNode} (copiés par
 * {@code GenerationService.buildInitialSections}) — {@code type}/{@code level}/
 * {@code columns} sont nécessaires à {@code GenerationStreamService} pour
 * assembler le bon niveau de titre Markdown et demander au fournisseur IA les
 * bonnes colonnes de tableau (au lieu de tout aplatir en niveau 2 générique) ;
 * {@code required}/{@code constraints} pilotent la validation post-génération
 * ({@code SectionConstraintValidator}) et le retry correctif.
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
    private String type;
    private Integer level;
    private List<String> columns;
    private Boolean required;
    private SectionConstraints constraints;
}
