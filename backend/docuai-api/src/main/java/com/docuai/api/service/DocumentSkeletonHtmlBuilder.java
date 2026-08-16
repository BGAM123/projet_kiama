package com.docuai.api.service;

import com.docuai.core.model.StructureNode;
import com.docuai.core.model.TableColumnDef;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduit le squelette d'un Document Type en document HTML prêt à être ouvert
 * dans l'éditeur type Word : c'est ce que l'utilisateur voit dès la création du
 * document, et ce qu'il modifie ensuite librement avant export.
 * <p>
 * Le HTML produit reste volontairement dans le sous-ensemble que l'éditeur
 * frontend sait représenter ({@code h1}..{@code h6}, {@code p}, {@code ul},
 * {@code table}/{@code th}/{@code td}) : un balisage plus riche serait
 * normalisé — donc perdu — au premier chargement de l'éditeur.
 */
@Component
public class DocumentSkeletonHtmlBuilder {

    private static final int DEFAULT_TABLE_ROWS = 3;
    private static final int MAX_TABLE_ROWS = 30;

    /**
     * @param tree arbre {@code document_structure.arbre_json} du Document Type
     * @return document HTML complet, jamais {@code null} — un squelette vide
     *         donne un paragraphe vide, pour que l'éditeur ait toujours un
     *         point d'insertion.
     */
    public String build(List<StructureNode> tree) {
        StringBuilder html = new StringBuilder();
        append(tree, html);
        return html.isEmpty() ? "<p></p>" : html.toString();
    }

    private void append(List<StructureNode> nodes, StringBuilder html) {
        if (nodes == null) {
            return;
        }
        for (StructureNode node : nodes) {
            String type = node.getType() == null ? "" : node.getType();
            switch (type) {
                case "cover" -> appendCover(node, html);
                case "table" -> appendTable(node, html);
                case "list" -> html.append("<ul><li><p>").append(escape(node.getLabel())).append("</p></li></ul>");
                // Emplacement à rédiger (flux "décrire en texte -> squelette IA") :
                // un paragraphe vide, pas le libellé de consigne — celui-ci
                // ressortirait tel quel dans le DOCX/PDF si l'utilisateur
                // oubliait de l'effacer.
                case "paragraph_placeholder" -> html.append("<p></p>");
                case "paragraph" -> html.append("<p>").append(escape(node.getLabel())).append("</p>");
                default -> appendHeading(node, html);
            }
            append(node.getChildren(), html);
        }
    }

    /** Page de garde : titre centré suivi d'un saut de page, comme dans le document source dont la structure a été extraite. */
    private void appendCover(StructureNode node, StringBuilder html) {
        html.append("<h1 style=\"text-align: center\">").append(escape(node.getLabel())).append("</h1>")
                .append("<div data-page-break></div>");
    }

    private void appendHeading(StructureNode node, StringBuilder html) {
        int level = node.getLevel() == null ? 1 : Math.max(1, Math.min(node.getLevel(), 6));
        html.append("<h").append(level).append('>').append(escape(node.getLabel())).append("</h").append(level).append('>');
        // Un titre feuille a besoin d'un paragraphe où écrire ; un titre qui
        // porte des sous-sections en aurait un inutile avant elles.
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            html.append("<p></p>");
        }
    }

    /**
     * Tableau pré-rempli de son en-tête. Deux origines possibles pour les
     * colonnes : {@code tableColumns} (squelette généré par IA, colonnes
     * typées) ou {@code columns} (extraction déterministe d'un fichier importé,
     * simples noms).
     */
    private void appendTable(StructureNode node, StringBuilder html) {
        List<String> headers = headersOf(node);
        int rows = Math.max(1, Math.min(node.getSuggestedRowCount() == null ? DEFAULT_TABLE_ROWS
                : node.getSuggestedRowCount(), MAX_TABLE_ROWS));

        if (node.getLabel() != null && !node.getLabel().isBlank()) {
            html.append("<p><strong>").append(escape(node.getLabel())).append("</strong></p>");
        }
        html.append("<table><tbody><tr>");
        headers.forEach(header -> html.append("<th><p>").append(escape(header)).append("</p></th>"));
        html.append("</tr>");
        for (int r = 0; r < rows; r++) {
            html.append("<tr>");
            headers.forEach(header -> html.append("<td><p></p></td>"));
            html.append("</tr>");
        }
        html.append("</tbody></table>");
    }

    private List<String> headersOf(StructureNode node) {
        if (node.getTableColumns() != null && !node.getTableColumns().isEmpty()) {
            return node.getTableColumns().stream().map(TableColumnDef::getName).toList();
        }
        if (node.getColumns() != null && !node.getColumns().isEmpty()) {
            return node.getColumns();
        }
        return List.of("Colonne 1", "Colonne 2");
    }

    /** Les libellés viennent d'un fichier importé ou d'un LLM : ils peuvent contenir n'importe quoi, y compris des chevrons. */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
