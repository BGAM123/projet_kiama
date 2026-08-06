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
 * Reconnaît uniquement le sous-ensemble Markdown effectivement produit par le
 * pipeline de génération : titres {@code #}..{@code ######}, tableaux
 * {@code | a | b |} avec leur ligne de séparation {@code |---|---|}, le reste
 * en paragraphes (une ligne vide sépare deux paragraphes).
 */
final class MarkdownContentParser {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.*)\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|?$");

    private MarkdownContentParser() {
    }

    sealed interface Block permits HeadingBlock, ParagraphBlock, TableBlock {
    }

    record HeadingBlock(int level, String text) implements Block {
    }

    record ParagraphBlock(String text) implements Block {
    }

    record TableBlock(List<List<String>> rows) implements Block {
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

    private static List<String> splitCells(String row) {
        String trimmed = row.substring(1, row.length() - 1);
        List<String> cells = new ArrayList<>();
        for (String cell : trimmed.split("\\|", -1)) {
            cells.add(cell.strip());
        }
        return cells;
    }
}
