package com.docuai.export;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Découpe un contenu Markdown (celui assemblé par
 * {@code GenerationStreamService}, ou édité manuellement côté frontend) en
 * blocs typés — utilisé par {@link DocxDocumentExporter} et
 * {@link PdfDocumentExporter} pour produire un vrai style de titre Word/une
 * taille de police par niveau et un vrai tableau, plutôt que du texte plat.
 * Reconnaît uniquement le sous-ensemble Markdown effectivement produit par
 * l'éditeur de sections du frontend : titres {@code #}..{@code ######},
 * tableaux {@code | a | b |} avec leur ligne de séparation {@code |---|---|},
 * listes à puces ({@code -}) et numérotées ({@code 1.}), filets horizontaux
 * ({@code ---}), le reste en paragraphes (une ligne vide sépare deux
 * paragraphes). À l'intérieur d'un paragraphe, d'un item de liste ou d'une
 * cellule, {@link #inline(String)} découpe les marques {@code **gras**},
 * {@code *italique*}, {@code `code`} et {@code [texte](url)} — sans quoi la
 * barre d'outils de l'éditeur produirait une mise en forme qui ressortirait en
 * caractères Markdown bruts dans le DOCX/PDF.
 */
final class MarkdownContentParser {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.*)\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|?$");
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("^(-{3,}|\\*{3,}|_{3,})$");
    private static final Pattern BULLET_ITEM = Pattern.compile("^([ \\t]*)[-*+]\\s+(.*)$");
    private static final Pattern ORDERED_ITEM = Pattern.compile("^([ \\t]*)\\d+[.)]\\s+(.*)$");
    /** Ordre significatif : `**` avant `*`, sinon un gras serait lu comme deux italiques vides. */
    private static final Pattern INLINE = Pattern.compile(
            "\\*\\*(?<bold>.+?)\\*\\*|\\*(?<italic>.+?)\\*|`(?<code>.+?)`|\\[(?<label>[^]]+)]\\((?<href>[^)\\s]+)\\)");

    private MarkdownContentParser() {
    }

    sealed interface Block permits HeadingBlock, ParagraphBlock, TableBlock, ListBlock, RuleBlock {
    }

    record HeadingBlock(int level, String text) implements Block {
    }

    record ParagraphBlock(String text) implements Block {
    }

    record TableBlock(List<List<String>> rows) implements Block {
    }

    /** {@code depth} = niveau d'imbrication (0 = premier niveau), déduit de l'indentation. */
    record ListItem(int depth, String text) {
    }

    record ListBlock(boolean ordered, List<ListItem> items) implements Block {
    }

    record RuleBlock() implements Block {
    }

    /** Fragment de texte homogène : {@code href} non nul = lien. */
    record Segment(String text, boolean bold, boolean italic, boolean code, String href) {
        static Segment plain(String text) {
            return new Segment(text, false, false, false, null);
        }
    }

    static List<Block> parse(String content) {
        List<Block> blocks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return blocks;
        }
        String[] lines = content.split("\n", -1);
        StringBuilder paragraph = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            var headingMatch = HEADING.matcher(line.strip());
            if (headingMatch.matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new HeadingBlock(headingMatch.group(1).length(), headingMatch.group(2).strip()));
                i++;
            } else if (TABLE_ROW.matcher(line.strip()).matches()) {
                flushParagraph(blocks, paragraph);
                List<List<String>> rows = new ArrayList<>();
                while (i < lines.length && TABLE_ROW.matcher(lines[i].strip()).matches()) {
                    String stripped = lines[i].strip();
                    if (!TABLE_SEPARATOR.matcher(stripped).matches()) {
                        rows.add(splitCells(stripped));
                    }
                    i++;
                }
                if (!rows.isEmpty()) {
                    blocks.add(new TableBlock(rows));
                }
            } else if (HORIZONTAL_RULE.matcher(line.strip()).matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new RuleBlock());
                i++;
            } else if (BULLET_ITEM.matcher(line).matches() || ORDERED_ITEM.matcher(line).matches()) {
                flushParagraph(blocks, paragraph);
                boolean ordered = ORDERED_ITEM.matcher(line).matches();
                List<ListItem> items = new ArrayList<>();
                // Une liste s'arrête au premier changement de nature (puces <->
                // numérotée) pour que le DOCX/PDF ne mélange pas les deux
                // rendus dans un même bloc.
                while (i < lines.length) {
                    var matcher = ordered ? ORDERED_ITEM.matcher(lines[i]) : BULLET_ITEM.matcher(lines[i]);
                    if (!matcher.matches()) {
                        break;
                    }
                    items.add(new ListItem(indentDepth(matcher.group(1)), matcher.group(2).strip()));
                    i++;
                }
                blocks.add(new ListBlock(ordered, items));
            } else if (line.isBlank()) {
                flushParagraph(blocks, paragraph);
                i++;
            } else {
                if (!paragraph.isEmpty()) {
                    paragraph.append(' ');
                }
                paragraph.append(line.strip());
                i++;
            }
        }
        flushParagraph(blocks, paragraph);
        return blocks;
    }

    private static void flushParagraph(List<Block> blocks, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            blocks.add(new ParagraphBlock(paragraph.toString()));
            paragraph.setLength(0);
        }
    }

    /** Une tabulation ou deux espaces valent un niveau — les deux conventions circulent, l'éditeur produisant des espaces. */
    private static int indentDepth(String indent) {
        int spaces = 0;
        for (char c : indent.toCharArray()) {
            spaces += c == '\t' ? 2 : 1;
        }
        return spaces / 2;
    }

    /**
     * Découpe un texte en fragments homogènes. Le texte hors marque est
     * conservé tel quel : ce parseur ne cherche pas à couvrir Markdown, juste
     * les marques que la barre d'outils de l'éditeur sait produire.
     */
    static List<Segment> inline(String text) {
        List<Segment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }
        var matcher = INLINE.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                segments.add(Segment.plain(text.substring(cursor, matcher.start())));
            }
            if (matcher.group("bold") != null) {
                segments.add(new Segment(matcher.group("bold"), true, false, false, null));
            } else if (matcher.group("italic") != null) {
                segments.add(new Segment(matcher.group("italic"), false, true, false, null));
            } else if (matcher.group("code") != null) {
                segments.add(new Segment(matcher.group("code"), false, false, true, null));
            } else {
                segments.add(new Segment(matcher.group("label"), false, false, false, matcher.group("href")));
            }
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            segments.add(Segment.plain(text.substring(cursor)));
        }
        return segments;
    }

    /** Texte débarrassé de ses marques, pour les rendus qui ne savent pas mélanger les polices (cellules de tableau PDF). */
    static String plainText(String text) {
        StringBuilder sb = new StringBuilder();
        for (Segment segment : inline(text)) {
            sb.append(segment.text());
        }
        return sb.toString();
    }

    private static List<String> splitCells(String row) {
        String trimmed = row.substring(1, row.length() - 1);
        List<String> cells = new ArrayList<>();
        for (String cell : trimmed.split("\\|", -1)) {
            cells.add(cell.strip());
        }
        return cells;
    }
}
