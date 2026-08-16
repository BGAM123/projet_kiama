package com.docuai.export;

import com.docuai.export.DocumentBlocks.Block;
import com.docuai.export.DocumentBlocks.HeadingBlock;
import com.docuai.export.DocumentBlocks.ImageBlock;
import com.docuai.export.DocumentBlocks.ListBlock;
import com.docuai.export.DocumentBlocks.ListItem;
import com.docuai.export.DocumentBlocks.PageBreakBlock;
import com.docuai.export.DocumentBlocks.ParagraphBlock;
import com.docuai.export.DocumentBlocks.RuleBlock;
import com.docuai.export.DocumentBlocks.Segment;
import com.docuai.export.DocumentBlocks.TableBlock;
import com.docuai.export.DocumentBlocks.TableCell;
import com.docuai.export.DocumentBlocks.TableRow;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Export Markdown. Le contenu Markdown hérité est recopié tel quel ; le
 * contenu de l'éditeur type Word (HTML) est resérialisé depuis les blocs.
 * <p>
 * Markdown ne sait pas représenter couleur, police, taille, alignement ni
 * fusion de cellules : ces attributs sont perdus ici, et c'est assumé — DOCX et
 * PDF sont les formats de livraison, Markdown reste un format d'échange texte.
 * Les images sont référencées comme telles ({@code ![image]}) mais leurs octets
 * ne sont pas écrits : un fichier Markdown n'embarque pas de binaire.
 */
@Component
public class MarkdownDocumentExporter implements DocumentExporter {

    @Override
    public ExportFormat supportedFormat() {
        return ExportFormat.MARKDOWN;
    }

    @Override
    public ExportedFile export(ExportContent request) {
        StringBuilder markdown = new StringBuilder();
        if (request.headerText() != null && !request.headerText().isBlank()) {
            markdown.append("<!-- en-tête : ").append(request.headerText()).append(" -->\n\n");
        }
        markdown.append("# ").append(request.resolvedTitle()).append("\n\n").append(body(request));
        if (request.footerText() != null && !request.footerText().isBlank()) {
            markdown.append("\n\n<!-- pied de page : ").append(request.footerText()).append(" -->");
        }
        return new ExportedFile(
                markdown.toString().getBytes(StandardCharsets.UTF_8),
                ExportFilenames.build(request.title(), "md"),
                "text/markdown; charset=UTF-8");
    }

    private String body(ExportContent request) {
        if (request.contentHtml() == null || request.contentHtml().isBlank()) {
            return request.content() == null ? "" : request.content();
        }
        StringBuilder sb = new StringBuilder();
        for (Block block : request.blocks()) {
            String rendered = render(block);
            if (rendered.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(rendered);
        }
        return sb.toString();
    }

    private String render(Block block) {
        if (block instanceof HeadingBlock heading) {
            return "#".repeat(Math.max(1, Math.min(heading.level(), 6))) + " " + inline(heading.segments());
        }
        if (block instanceof ParagraphBlock paragraph) {
            return inline(paragraph.segments());
        }
        if (block instanceof ListBlock list) {
            return renderList(list);
        }
        if (block instanceof TableBlock table) {
            return renderTable(table);
        }
        if (block instanceof ImageBlock) {
            return "![image]()";
        }
        if (block instanceof RuleBlock) {
            return "---";
        }
        if (block instanceof PageBreakBlock) {
            return "<!-- saut de page -->";
        }
        return "";
    }

    private String renderList(ListBlock list) {
        StringBuilder sb = new StringBuilder();
        int[] counters = new int[16];
        int previousDepth = 0;
        for (ListItem item : list.items()) {
            int depth = Math.max(0, Math.min(item.depth(), counters.length - 1));
            if (depth > previousDepth) {
                counters[depth] = 0;
            }
            counters[depth]++;
            previousDepth = depth;
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("  ".repeat(depth))
                    .append(list.ordered() ? counters[depth] + ". " : "- ")
                    .append(inline(item.segments()));
        }
        return sb.toString();
    }

    private String renderTable(TableBlock table) {
        List<TableRow> rows = table.rows();
        if (rows.isEmpty()) {
            return "";
        }
        int columnCount = rows.stream().mapToInt(row -> row.cells().size()).max().orElse(0);
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            sb.append(renderRow(rows.get(r), columnCount)).append('\n');
            if (r == 0) {
                sb.append("| ").append(String.join(" | ", java.util.Collections.nCopies(columnCount, "---")))
                        .append(" |\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    private String renderRow(TableRow row, int columnCount) {
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < columnCount; c++) {
            TableCell cell = c < row.cells().size() ? row.cells().get(c) : null;
            String text = cell == null ? "" : inline(cell.segments());
            // Un pipe ou un retour à la ligne dans une cellule casserait la
            // ligne du tableau : échappé/replié comme le fait l'éditeur.
            sb.append(' ').append(text.replace("|", "\\|").replaceAll("\\s*\\n\\s*", " ")).append(" |");
        }
        return sb.toString();
    }

    /** Seules les marques ayant une syntaxe Markdown sont écrites ; le reste (couleur, taille…) tombe silencieusement, faute de représentation. */
    private String inline(List<Segment> segments) {
        StringBuilder sb = new StringBuilder();
        for (Segment segment : segments) {
            String text = segment.text();
            if (text.isEmpty()) {
                continue;
            }
            if (segment.code()) {
                text = "`" + text + "`";
            } else {
                if (segment.bold()) {
                    text = "**" + text + "**";
                }
                if (segment.italic()) {
                    text = "*" + text + "*";
                }
            }
            if (segment.href() != null) {
                text = "[" + text + "](" + segment.href() + ")";
            }
            sb.append(text);
        }
        return sb.toString();
    }
}
