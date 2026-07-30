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
    private List<String> columns;
}
