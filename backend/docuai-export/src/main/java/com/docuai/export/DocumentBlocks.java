package com.docuai.export;

import java.util.List;

/**
 * Format pivot des exporteurs : ce que {@link MarkdownContentParser} (contenu
 * hérité, assemblé section par section) et {@link HtmlContentParser} (contenu
 * produit par l'éditeur type Word du frontend) produisent tous les deux, et ce
 * que {@link DocxDocumentExporter}/{@link PdfDocumentExporter} savent rendre.
 * <p>
 * Le modèle est volontairement plus riche que le Markdown : l'éditeur expose
 * désormais police, taille, couleur, surlignage, alignement, images et sauts de
 * page, qui n'ont aucune représentation Markdown. Un parseur qui ne sait pas
 * produire un attribut le laisse simplement à {@code null} — les exporteurs
 * retombent alors sur leurs valeurs par défaut, et rien n'est perdu au passage
 * pour les contenus anciens.
 */
public final class DocumentBlocks {

    private DocumentBlocks() {
    }

    /** Alignement d'un bloc. {@code null} = alignement par défaut du document (gauche). */
    public enum Align {
        LEFT,
        CENTER,
        RIGHT,
        JUSTIFY;

        /** Tolérant aux valeurs CSS inconnues ({@code start}, {@code end}, vide) — renvoie {@code null} plutôt que d'échouer l'export. */
        public static Align from(String css) {
            if (css == null) {
                return null;
            }
            return switch (css.trim().toLowerCase()) {
                case "center" -> CENTER;
                case "right", "end" -> RIGHT;
                case "justify" -> JUSTIFY;
                case "left", "start" -> LEFT;
                default -> null;
            };
        }
    }

    /**
     * Fragment de texte homogène. Les attributs optionnels sont {@code null}
     * quand ils ne sont pas fixés, pour que l'exporteur distingue « couleur
     * noire explicite » de « pas de couleur demandée ».
     *
     * @param color      couleur du texte en hexadécimal sans {@code #} (ex. {@code "C00000"})
     * @param highlight  couleur de fond du texte, même convention
     * @param fontSize   taille en points
     * @param fontFamily nom de police tel qu'il sera écrit dans le DOCX
     */
    public record Segment(String text,
                          boolean bold,
                          boolean italic,
                          boolean underline,
                          boolean strike,
                          boolean code,
                          boolean superscript,
                          boolean subscript,
                          String href,
                          String color,
                          String highlight,
                          Integer fontSize,
                          String fontFamily) {

        public static Segment plain(String text) {
            return new Segment(text, false, false, false, false, false, false, false, null, null, null, null, null);
        }

        public Segment withText(String value) {
            return new Segment(value, bold, italic, underline, strike, code, superscript, subscript,
                    href, color, highlight, fontSize, fontFamily);
        }

        public Segment bold(boolean value) {
            return new Segment(text, value, italic, underline, strike, code, superscript, subscript,
                    href, color, highlight, fontSize, fontFamily);
        }

        public Segment italic(boolean value) {
            return new Segment(text, bold, value, underline, strike, code, superscript, subscript,
                    href, color, highlight, fontSize, fontFamily);
        }

        public Segment underline(boolean value) {
            return new Segment(text, bold, italic, value, strike, code, superscript, subscript,
                    href, color, highlight, fontSize, fontFamily);
        }

        public Segment strike(boolean value) {
            return new Segment(text, bold, italic, underline, value, code, superscript, subscript,
                    href, color, highlight, fontSize, fontFamily);
        }

        public Segment code(boolean value) {
            return new Segment(text, bold, italic, underline, strike, value, superscript, subscript,
                    href, color, highlight, fontSize, fontFamily);
        }

        /** Exposant et indice s'excluent — poser l'un retire l'autre, comme dans un traitement de texte. */
        public Segment asSuperscript() {
            return new Segment(text, bold, italic, underline, strike, code, true, false,
                    href, color, highlight, fontSize, fontFamily);
        }

        public Segment asSubscript() {
            return new Segment(text, bold, italic, underline, strike, code, false, true,
                    href, color, highlight, fontSize, fontFamily);
        }

        public Segment href(String value) {
            return new Segment(text, bold, italic, underline, strike, code, superscript, subscript,
                    value, color, highlight, fontSize, fontFamily);
        }

        public Segment color(String value) {
            return new Segment(text, bold, italic, underline, strike, code, superscript, subscript,
                    href, value, highlight, fontSize, fontFamily);
        }

        public Segment highlight(String value) {
            return new Segment(text, bold, italic, underline, strike, code, superscript, subscript,
                    href, color, value, fontSize, fontFamily);
        }

        public Segment fontSize(Integer value) {
            return new Segment(text, bold, italic, underline, strike, code, superscript, subscript,
                    href, color, highlight, value, fontFamily);
        }

        public Segment fontFamily(String value) {
            return new Segment(text, bold, italic, underline, strike, code, superscript, subscript,
                    href, color, highlight, fontSize, value);
        }
    }

    public sealed interface Block
            permits HeadingBlock, ParagraphBlock, ListBlock, TableBlock, ImageBlock, RuleBlock, PageBreakBlock {
    }

    public record HeadingBlock(int level, List<Segment> segments, Align align) implements Block {
    }

    public record ParagraphBlock(List<Segment> segments, Align align) implements Block {
    }

    /** {@code depth} = niveau d'imbrication (0 = premier niveau). */
    public record ListItem(int depth, List<Segment> segments) {
    }

    public record ListBlock(boolean ordered, List<ListItem> items) implements Block {
    }

    /**
     * Cellule de tableau. {@code colSpan}/{@code rowSpan} valent 1 par défaut ;
     * {@link PdfDocumentExporter} les ignore (grille dessinée à la main) tandis
     * que {@link DocxDocumentExporter} les traduit en fusion Word réelle.
     */
    public record TableCell(List<Segment> segments, boolean header, int colSpan, int rowSpan, Align align) {

        public static TableCell of(List<Segment> segments, boolean header) {
            return new TableCell(segments, header, 1, 1, null);
        }
    }

    public record TableRow(List<TableCell> cells) {
    }

    public record TableBlock(List<TableRow> rows) implements Block {
    }

    /**
     * Image décodée, prête à être écrite dans le fichier produit.
     *
     * @param data        octets bruts de l'image
     * @param contentType type MIME source (ex. {@code image/png})
     * @param widthPx     largeur souhaitée en pixels CSS, {@code null} si l'image doit garder sa taille naturelle
     */
    public record ImageBlock(byte[] data, String contentType, Integer widthPx, Align align) implements Block {
    }

    public record RuleBlock() implements Block {
    }

    public record PageBreakBlock() implements Block {
    }

    /** Texte lisible d'une suite de fragments, pour les rendus qui ne savent pas mélanger les polices. */
    public static String plainText(List<Segment> segments) {
        StringBuilder sb = new StringBuilder();
        for (Segment segment : segments) {
            sb.append(segment.text());
        }
        return sb.toString();
    }
}
