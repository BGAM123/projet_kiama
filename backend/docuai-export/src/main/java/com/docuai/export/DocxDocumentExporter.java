package com.docuai.export;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/**
 * Export DOCX qui interprète le Markdown assemblé par la génération
 * ({@link MarkdownContentParser}) au lieu d'écrire chaque ligne comme un
 * paragraphe plat : titres avec mise en forme + niveau de plan réel (repris
 * dans le volet de navigation Word), tableaux comme de vraies
 * {@link XWPFTable}, en-tête/pied de page statiques si fournis.
 */
@Component
public class DocxDocumentExporter implements DocumentExporter {

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int[] HEADING_FONT_SIZE = {20, 16, 14, 13, 12, 11};
    /** En twips (1/20 de point) — ~0,6 cm par niveau d'imbrication. */
    private static final int LIST_INDENT_PER_LEVEL = 360;

    @Override
    public ExportFormat supportedFormat() {
        return ExportFormat.DOCX;
    }

    @Override
    public ExportedFile export(ExportContent request) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeHeaderFooter(document, request.headerText(), request.footerText());

            XWPFParagraph titleParagraph = document.createParagraph();
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText((request.title() == null || request.title().isBlank()) ? "Document" : request.title());
            titleRun.setBold(true);
            titleRun.setFontSize(22);

            for (MarkdownContentParser.Block block : MarkdownContentParser.parse(request.content())) {
                if (block instanceof MarkdownContentParser.HeadingBlock heading) {
                    writeHeading(document, heading);
                } else if (block instanceof MarkdownContentParser.TableBlock table) {
                    writeTable(document, table);
                } else if (block instanceof MarkdownContentParser.ParagraphBlock paragraph) {
                    writeSegments(document.createParagraph(), paragraph.text());
                } else if (block instanceof MarkdownContentParser.ListBlock list) {
                    writeList(document, list);
                } else if (block instanceof MarkdownContentParser.RuleBlock) {
                    writeHorizontalRule(document);
                }
            }

            document.write(out);
            return new ExportedFile(out.toByteArray(), ExportFilenames.build(request.title(), "docx"), CONTENT_TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Échec de génération du document DOCX.", e);
        }
    }

    private void writeHeading(XWPFDocument document, MarkdownContentParser.HeadingBlock heading) {
        int level = Math.max(1, Math.min(heading.level(), 6));
        XWPFParagraph paragraph = document.createParagraph();
        // Niveau de plan natif (w:outlineLvl) : Word reconnaît la ligne comme
        // un titre (volet de navigation, table des matières) même sans style
        // "HeadingN" défini dans le document (un XWPFDocument vierge n'a pas
        // de styles.xml) — plus robuste qu'un setStyle() qui pointerait vers
        // un style inexistant.
        CTPPr ppr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTDecimalNumber outlineLvl = ppr.addNewOutlineLvl();
        outlineLvl.setVal(BigInteger.valueOf(level - 1));

        XWPFRun run = paragraph.createRun();
        run.setText(heading.text());
        run.setBold(true);
        run.setFontSize(HEADING_FONT_SIZE[level - 1]);
    }

    /**
     * Un run Word par fragment de mise en forme — c'est ce qui évite que le
     * {@code **gras**} saisi dans l'éditeur ressorte en astérisques littérales
     * dans le document exporté. Les liens sont rendus en bleu souligné plutôt
     * qu'en vrai champ HYPERLINK : l'apparence attendue sans la plomberie de
     * relations OOXML.
     */
    private void writeSegments(XWPFParagraph paragraph, String text) {
        writeSegments(paragraph, text, false);
    }

    private void writeSegments(XWPFParagraph paragraph, String text, boolean forceBold) {
        for (MarkdownContentParser.Segment segment : MarkdownContentParser.inline(text)) {
            XWPFRun run = paragraph.createRun();
            run.setText(segment.text());
            run.setBold(forceBold || segment.bold());
            run.setItalic(segment.italic());
            if (segment.code()) {
                run.setFontFamily("Consolas");
            }
            if (segment.href() != null) {
                run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
                run.setColor("1155CC");
            }
        }
    }

    /**
     * Puces et numéros écrits en texte, avec un retrait proportionnel au
     * niveau : un XWPFDocument vierge n'embarque pas de numbering.xml, et en
     * créer un pour trois niveaux de liste coûterait plus qu'il ne rapporte.
     */
    private void writeList(XWPFDocument document, MarkdownContentParser.ListBlock list) {
        int counter = 0;
        for (MarkdownContentParser.ListItem item : list.items()) {
            counter++;
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(LIST_INDENT_PER_LEVEL * (item.depth() + 1));
            paragraph.createRun().setText(list.ordered() ? counter + ". " : "• ");
            writeSegments(paragraph, item.text());
        }
    }

    private void writeHorizontalRule(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
    }

    private void writeTable(XWPFDocument document, MarkdownContentParser.TableBlock tableBlock) {
        List<List<String>> rows = tableBlock.rows();
        if (rows.isEmpty()) {
            return;
        }
        int columnCount = rows.stream().mapToInt(List::size).max().orElse(0);
        if (columnCount == 0) {
            return;
        }
        XWPFTable table = document.createTable(rows.size(), columnCount);
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < columnCount; c++) {
                XWPFTableCell cell = table.getRow(r).getCell(c);
                cell.removeParagraph(0);
                XWPFParagraph cellParagraph = cell.addParagraph();
                writeSegments(cellParagraph, c < row.size() ? row.get(c) : "", r == 0);
            }
        }
    }

    private void writeHeaderFooter(XWPFDocument document, String headerText, String footerText) {
        if ((headerText == null || headerText.isBlank()) && (footerText == null || footerText.isBlank())) {
            return;
        }
        XWPFHeaderFooterPolicy policy = document.createHeaderFooterPolicy();
        if (headerText != null && !headerText.isBlank()) {
            XWPFHeader header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT);
            header.createParagraph().createRun().setText(headerText);
        }
        if (footerText != null && !footerText.isBlank()) {
            XWPFFooter footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
            footer.createParagraph().createRun().setText(footerText);
        }
    }
}
