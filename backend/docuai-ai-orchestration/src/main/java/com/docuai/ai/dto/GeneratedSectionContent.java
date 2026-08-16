package com.docuai.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Contenu généré par le LLM pour une section précise du plan d'un document
 * (flux "Générer avec l'IA" du document lui-même, cf.
 * {@link com.docuai.ai.service.PromptBuilder#buildDocumentContentSystemPrompt}),
 * telle que {@link com.docuai.ai.service.DocumentContentResponseParser}
 * l'extrait de la réponse JSON du fournisseur.
 * <p>
 * {@code id} correspond à l'identifiant du {@code StructureNode} ciblé — c'est
 * ce qui permet de reporter le contenu généré sur le bon nœud du plan sans
 * dépendre de l'ordre de la réponse. Exactement un des deux champs {@code
 * content}/{@code rows} est renseigné selon que la section est un paragraphe
 * ou un tableau ; Lombok/Jackson en mutable POJO (même parti pris que {@link
 * com.docuai.core.model.StructureNode}) pour une désérialisation directe sans
 * builder Jackson dédié.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedSectionContent {
    private String id;
    /** Texte du paragraphe généré — {@code null} pour une section de type tableau. */
    private String content;
    /** Lignes du tableau généré (une valeur par colonne, dans l'ordre du plan) — {@code null} pour une section de type paragraphe. */
    private List<List<String>> rows;
}
