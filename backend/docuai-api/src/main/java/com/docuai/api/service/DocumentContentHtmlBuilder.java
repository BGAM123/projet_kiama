package com.docuai.api.service;

import com.docuai.ai.dto.GeneratedSectionContent;
import com.docuai.core.model.StructureNode;
import com.docuai.core.model.TableColumnDef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Traduit le plan d'un Document Type en document HTML prêt à être ouvert dans
 * l'éditeur type Word — squelette vide ({@link DocumentSkeletonHtmlBuilder},
 * qui délègue ici avec une correspondance vide) ou rempli du contenu généré
 * par l'IA (flux "Générer avec l'IA" du document, cf. {@code
 * DocumentContentGenerationService}).
 * <p>
 * Un nœud sans correspondance dans {@code generatedById} — parce que la
 * génération n'a pas été demandée, ou parce que le modèle a oublié cette
 * section dans sa réponse — retombe exactement sur le rendu vide du
 * squelette : une génération partielle ne produit jamais de trou visible
 * inattendu, seulement une section à rédiger manuellement comme n'importe
 * quel squelette.
 */
@Component
public class DocumentContentHtmlBuilder {

    private static final int DEFAULT_TABLE_ROWS = 3;
    private static final int MAX_TABLE_ROWS = 30;

    /**
     * @param tree          arbre {@code document_structure.arbre_json} du Document Type
     * @param generatedById contenu généré par l'IA, indexé par {@code StructureNode.id} — vide pour un squelette sans génération
     * @return document HTML complet, jamais {@code null}
     */
    public String build(List<StructureNode> tree, Map<String, GeneratedSectionContent> generatedById) {
        StringBuilder html = new StringBuilder();
        append(tree, html, generatedById);
        return html.isEmpty() ? "<p></p>" : html.toString();
    }

    private void append(List<StructureNode> nodes, StringBuilder html, Map<String, GeneratedSectionContent> byId) {
        if (nodes == null) {
            return;
        }
        for (StructureNode node : nodes) {
            String type = node.getType() == null ? "" : node.getType();
            switch (type) {
                case "cover" -> appendCover(node, html);
                case "table" -> appendTable(node, html, byId.get(node.getId()));
                case "list" -> appendList(node, html, byId.get(node.getId()));
                // Emplacement à rédiger (flux "décrire en texte -> squelette IA") :
                // seul ce type reçoit du contenu généré. "paragraph" (extraction
                // déterministe d'un fichier importé) porte déjà son texte réel
                // dans le label — jamais une cible de génération, voir
                // appendParagraph ci-dessous.
                case "paragraph_placeholder" -> appendParagraphBody(html, byId.get(node.getId()));
                case "paragraph" -> html.append("<p>").append(escape(node.getLabel())).append("</p>");
                default -> appendHeading(node, html, byId.get(node.getId()));
            }
            append(node.getChildren(), html, byId);
        }
    }

    /** Page de garde : titre centré suivi d'un saut de page, comme dans le document source dont la structure a été extraite. */
    private void appendCover(StructureNode node, StringBuilder html) {
        html.append("<h1 style=\"text-align: center\">").append(escape(node.getLabel())).append("</h1>")
                .append("<div data-page-break></div>");
    }

    private void appendHeading(StructureNode node, StringBuilder html, GeneratedSectionContent generated) {
        int level = node.getLevel() == null ? 1 : Math.max(1, Math.min(node.getLevel(), 6));
        html.append("<h").append(level).append('>').append(escape(node.getLabel())).append("</h").append(level).append('>');
        // Un titre feuille a besoin d'un paragraphe où écrire (généré ou vide) ;
        // un titre qui porte des sous-sections en aurait un inutile avant elles.
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            appendParagraphBody(html, generated);
        }
    }

    /** Texte généré réparti sur autant de {@code <p>} qu'il contient de paragraphes (séparés par une ligne blanche) — vide, un simple paragraphe à rédiger comme dans le squelette. */
    private void appendParagraphBody(StringBuilder html, GeneratedSectionContent generated) {
        String content = generated == null ? null : generated.getContent();
        if (content == null || content.isBlank()) {
            html.append("<p></p>");
            return;
        }
        for (String block : content.strip().split("\n\\s*\n")) {
            String trimmed = block.strip();
            if (!trimmed.isEmpty()) {
                html.append("<p>").append(escape(trimmed).replace("\n", "<br>")).append("</p>");
            }
        }
    }

    /** Une ligne générée par item — vide, retombe sur l'unique item "label" du squelette (déjà informatif à défaut de contenu). */
    private void appendList(StructureNode node, StringBuilder html, GeneratedSectionContent generated) {
        String content = generated == null ? null : generated.getContent();
        if (content == null || content.isBlank()) {
            html.append("<ul><li><p>").append(escape(node.getLabel())).append("</p></li></ul>");
            return;
        }
        html.append("<ul>");
        for (String line : content.strip().split("\n")) {
            String trimmed = line.strip().replaceFirst("^[-*•]\\s*", "");
            if (!trimmed.isEmpty()) {
                html.append("<li><p>").append(escape(trimmed)).append("</p></li>");
            }
        }
        html.append("</ul>");
    }

    /**
     * Tableau pré-rempli de son en-tête, et de ses lignes si générées. Les
     * lignes générées sont bornées aux dimensions attendues (colonnes du plan,
     * {@code suggestedRowCount}) — une réponse IA qui en aurait produit plus ou
     * moins est tronquée/complétée par des cellules vides plutôt que de casser
     * la grille.
     */
    private void appendTable(StructureNode node, StringBuilder html, GeneratedSectionContent generated) {
        List<String> headers = headersOf(node);
        int rowCount = Math.max(1, Math.min(node.getSuggestedRowCount() == null ? DEFAULT_TABLE_ROWS
                : node.getSuggestedRowCount(), MAX_TABLE_ROWS));
        List<List<String>> generatedRows = generated != null && generated.getRows() != null
                ? generated.getRows() : List.of();

        if (node.getLabel() != null && !node.getLabel().isBlank()) {
            html.append("<p><strong>").append(escape(node.getLabel())).append("</strong></p>");
        }
        html.append("<table><tbody><tr>");
        headers.forEach(header -> html.append("<th><p>").append(escape(header)).append("</p></th>"));
        html.append("</tr>");
        for (int r = 0; r < rowCount; r++) {
            List<String> row = r < generatedRows.size() ? generatedRows.get(r) : List.of();
            html.append("<tr>");
            for (int c = 0; c < headers.size(); c++) {
                String cell = c < row.size() ? row.get(c) : "";
                html.append("<td><p>").append(escape(cell)).append("</p></td>");
            }
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
