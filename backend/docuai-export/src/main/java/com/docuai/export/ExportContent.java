package com.docuai.export;

import java.util.List;

/**
 * Entrée d'un {@link DocumentExporter}.
 * <p>
 * Deux sources de contenu possibles, dans cet ordre de priorité :
 * <ul>
 *   <li>{@code contentHtml} — le document tel qu'il a été mis en forme dans
 *       l'éditeur type Word du frontend. C'est le cas nominal depuis la refonte
 *       de la génération : seul ce format porte les polices, tailles, couleurs,
 *       alignements, images et sauts de page.</li>
 *   <li>{@code content} — Markdown assemblé section par section, format des
 *       documents créés avant cette refonte. Conservé pour qu'ils restent
 *       exportables.</li>
 * </ul>
 * {@code headerText}/{@code footerText} sont optionnels (texte statique extrait
 * du Document Type source, jamais généré par l'IA — voir
 * {@code DocumentStructure.headerText}/{@code footerText}).
 */
public record ExportContent(String title, String content, String contentHtml, String headerText, String footerText) {

    /** Contenu Markdown seul — signature historique conservée pour les appelants qui n'ont pas de HTML (export ad hoc via {@code POST /export}). */
    public ExportContent(String title, String content, String headerText, String footerText) {
        this(title, content, null, headerText, footerText);
    }

    /** Blocs à rendre, quelle que soit la source : c'est ici que se décide HTML vs Markdown, une seule fois pour les trois exporteurs. */
    public List<DocumentBlocks.Block> blocks() {
        if (contentHtml != null && !contentHtml.isBlank()) {
            return HtmlContentParser.parse(contentHtml);
        }
        return MarkdownContentParser.parse(content);
    }

    public String resolvedTitle() {
        return (title == null || title.isBlank()) ? "Document" : title;
    }
}
