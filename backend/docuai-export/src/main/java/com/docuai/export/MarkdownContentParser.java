package com.docuai.export;

import com.docuai.export.DocumentBlocks.Block;
import com.docuai.export.DocumentBlocks.HeadingBlock;
import com.docuai.export.DocumentBlocks.ListBlock;
import com.docuai.export.DocumentBlocks.ListItem;
import com.docuai.export.DocumentBlocks.ParagraphBlock;
import com.docuai.export.DocumentBlocks.RuleBlock;
import com.docuai.export.DocumentBlocks.Segment;
import com.docuai.export.DocumentBlocks.TableBlock;
import com.docuai.export.DocumentBlocks.TableCell;
import com.docuai.export.DocumentBlocks.TableRow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Découpe un contenu Markdown en {@link DocumentBlocks.Block} — format hérité
 * du flux d'édition section par section, conservé pour les documents créés
 * avant l'éditeur type Word (dont le contenu est du HTML, cf.
 * {@link HtmlContentParser}) et pour l'assemblage serveur des sections.
 * Reconnaît : titres {@code #}..{@code ######}, tableaux {@code | a | b |}
 * avec leur ligne de séparation {@code |---|---|}, listes à puces ({@code -})
 * et numérotées ({@code 1.}), filets horizontaux ({@code ---}), le reste en
 * paragraphes (une ligne vide sépare deux paragraphes). À l'intérieur d'un
 * paragraphe, d'un item de liste ou d'une cellule, {@link #inline(String)}
 * découpe les marques {@code **gras**}, {@code *italique*}, {@code `code`} et
 * {@code [texte](url)}.
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
                blocks.add(new HeadingBlock(headingMatch.group(1).length(),
                        inline(headingMatch.group(2).strip()), null));
                i++;
            } else if (TABLE_ROW.matcher(line.strip()).matches()) {
                flushParagraph(blocks, paragraph);
                List<TableRow> rows = new ArrayList<>();
                while (i < lines.length && TABLE_ROW.matcher(lines[i].strip()).matches()) {
                    String stripped = lines[i].strip();
                    if (!TABLE_SEPARATOR.matcher(stripped).matches()) {
                        // Convention Markdown : la première ligne du tableau est
                        // l'en-tête (c'est ce que produit l'éditeur, et ce que
                        // les exporteurs mettaient déjà en gras).
                        boolean header = rows.isEmpty();
                        rows.add(new TableRow(splitCells(stripped).stream()
                                .map(cell -> TableCell.of(inline(cell), header))
                                .toList()));
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
                    items.add(new ListItem(indentDepth(matcher.group(1)), inline(matcher.group(2).strip())));
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
            blocks.add(new ParagraphBlock(inline(paragraph.toString()), null));
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
     * les marques que l'éditeur Markdown historique savait produire.
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
                segments.add(mark(matcher.group("bold"), true, false, false, null));
            } else if (matcher.group("italic") != null) {
                segments.add(mark(matcher.group("italic"), false, true, false, null));
            } else if (matcher.group("code") != null) {
                segments.add(mark(matcher.group("code"), false, false, true, null));
            } else {
                segments.add(mark(matcher.group("label"), false, false, false, matcher.group("href")));
            }
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            segments.add(Segment.plain(text.substring(cursor)));
        }
        return segments;
    }

    private static Segment mark(String text, boolean bold, boolean italic, boolean code, String href) {
        return new Segment(text, bold, italic, false, false, code, false, false, href, null, null, null, null);
    }

    /** Texte débarrassé de ses marques, pour les rendus qui ne savent pas mélanger les polices. */
    static String plainText(String text) {
        return DocumentBlocks.plainText(inline(text));
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
