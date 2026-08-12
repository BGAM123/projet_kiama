package com.docuai.core.model;

import lombok.*;

/**
 * Colonne attendue d'une section {@code type == "table"} — porté par
 * {@link StructureNode#getTableColumns()}. Distinct de
 * {@link StructureNode#getColumns()} (simples noms de colonnes, produits par
 * l'extraction déterministe d'un fichier importé, Bloc 4) : {@code
 * tableColumns} porte en plus un type de colonne indicatif, renseigné par le
 * flux de génération de squelette par IA (texte -> structure).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableColumnDef {
    private String name;
    /** Type indicatif de la colonne (ex. "text", "date", "number") — informatif, non validé par contrainte. */
    private String type;
}
