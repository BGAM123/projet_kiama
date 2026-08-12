package com.docuai.core.model;

import lombok.*;

import java.util.List;

/**
 * Nœud de l'arbre de structure d'un Document Type (pas une entité JPA — un
 * élément de la valeur JSONB {@code document_structure.arbre_json},
 * sérialisé/désérialisé par Hibernate 6 via {@code @JdbcTypeCode(SqlTypes.JSON)}
 * sur {@link DocumentStructure#getArbreJson()}). Les noms de champs
 * correspondent exactement au type frontend {@code StructureNode}
 * (frontend/types/index.ts) pour que la sérialisation Jackson standard
 * produise le même JSON sans mapping manuel.
 * <p>
 * {@code type} vaut {@code heading|paragraph|table|list|cover} (extraction
 * déterministe d'un fichier importé, Bloc 4) ou {@code paragraph_placeholder}
 * (nouveau, flux "décrire en texte -> squelette généré par IA" — un
 * emplacement de paragraphe à rédiger manuellement, jamais de contenu). La
 * hiérarchie titre/sous-titre/sous-sous-titre est portée par {@code
 * type == "heading"} + {@code level} (1/2/3), pas par des valeurs de
 * {@code type} distinctes — cohérent avec {@code PromptBuilder.renderStructureBlock}
 * et l'éditeur de structure frontend, qui reposent déjà tous les deux sur ce
 * couple.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureNode {
    private String id;
    private String type;
    private Integer level;
    private String label;
    private List<StructureNode> children;
    /** Noms de colonnes bruts — extraction déterministe d'un fichier importé (Bloc 4). */
    private List<String> columns;
    /** Colonnes typées d'une section {@code type == "table"} — flux de génération de squelette par IA uniquement, {@code null} pour les structures importées. */
    private List<TableColumnDef> tableColumns;
    /** Nombre de lignes suggéré pour une section {@code type == "table"} — informatif, flux IA uniquement. */
    private Integer suggestedRowCount;
    /** Section obligatoire — {@code null} traité comme {@code true} (comportement historique, tous les nœuds étaient implicitement requis avant l'ajout de ce champ). */
    private Boolean required;
    private SectionConstraints constraints;
}
