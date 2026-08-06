package com.docuai.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Export PDF qui interprète le Markdown assemblé par la génération
 * ({@link MarkdownContentParser}) : titres avec taille de police dégressive
 * par niveau, tableaux dessinés en grille (PDFBox n'a pas d'API tableau native
 * contrairement à Apache POI), en-tête/pied de page répétés sur chaque page
 * (ajoutés en second passage une fois la pagination connue).
 */
@Component
public class PdfDocumentExporter implements DocumentExporter {

    private static final float MARGIN = 50f;
    private static final float TITLE_FONT_SIZE = 18f;
    private static final float BODY_FONT_SIZE = 11f;
    private static final float LINE_HEIGHT = 15f;
    private static final float[] HEADING_FONT_SIZE = {18f, 15f, 13f, 12f, 11f, 11f};
    private static final float HEADER_FOOTER_FONT_SIZE = 9f;
    private static final float TABLE_CELL_PADDING = 4f;

    @Override
    public ExportFormat supportedFormat() {
        return ExportFormat.PDF;
    }

    @Override
    public ExportedFile export(ExportContent request) {
        boolean hasHeader = request.headerText() != null && !request.headerText().isBlank();
        boolean hasFooter = request.footerText() != null && !request.footerText().isBlank();
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Cursor cursor = new Cursor(document, hasHeader, hasFooter);
            cursor.writeTitle((request.title() == null || request.title().isBlank()) ? "Document" : request.title());

            for (MarkdownContentParser.Block block : MarkdownContentParser.parse(request.content())) {
                if (block instanceof MarkdownContentParser.HeadingBlock heading) {
                    cursor.writeHeading(heading);
                } else if (block instanceof MarkdownContentParser.ParagraphBlock paragraph) {
                    cursor.writeParagraph(paragraph.text());
                } else if (block instanceof MarkdownContentParser.TableBlock table) {
                    cursor.writeTable(table.rows());
                }
            }
            cursor.finish(request.headerText(), request.footerText());

            document.save(out);
            return new ExportedFile(out.toByteArray(), ExportFilenames.build(request.title(), "pdf"), "application/pdf");
        } catch (IOException e) {
            throw new IllegalStateException("Échec de génération du document PDF.", e);
        }
    }

    /** Curseur d'écriture mutable — encapsule la pagination pour éviter de faire circuler page/stream/y entre des méthodes statiques. */
    private static final class Cursor {
        private final PDDocument document;
        private final PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final float pageWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;
        private final float topMargin;
        private final float bottomMargin;
        private final List<PDPage> pages = new ArrayList<>();
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        Cursor(PDDocument document, boolean hasHeader, boolean hasFooter) throws IOException {
            this.document = document;
            this.topMargin = MARGIN + (hasHeader ? LINE_HEIGHT + 6 : 0);
            this.bottomMargin = MARGIN + (hasFooter ? LINE_HEIGHT + 6 : 0);
            newPage();
        }

        void writeTitle(String title) throws IOException {
            text(boldFont, TITLE_FONT_SIZE, title);
            y -= TITLE_FONT_SIZE + LINE_HEIGHT;
        }

        void writeHeading(MarkdownContentParser.HeadingBlock heading) throws IOException {
            int level = Math.max(1, Math.min(heading.level(), 6));
            float size = HEADING_FONT_SIZE[level - 1];
            ensureSpace(size + LINE_HEIGHT);
            y -= LINE_HEIGHT / 2;
            text(boldFont, size, heading.text());
            y -= size + LINE_HEIGHT / 2;
        }

        void writeParagraph(String paragraph) throws IOException {
            for (String wrapped : wrap(paragraph, bodyFont, BODY_FONT_SIZE, pageWidth)) {
                ensureSpace(LINE_HEIGHT);
                text(bodyFont, BODY_FONT_SIZE, wrapped);
                y -= LINE_HEIGHT;
            }
            y -= LINE_HEIGHT / 2;
        }

        void writeTable(List<List<String>> rows) throws IOException {
            if (rows.isEmpty()) {
                return;
            }
            int columnCount = rows.stream().mapToInt(List::size).max().orElse(0);
            if (columnCount == 0) {
                return;
            }
            float colWidth = pageWidth / columnCount;
            for (List<String> row : rows) {
                List<List<String>> wrappedCells = new ArrayList<>();
                int lineCount = 1;
                for (int c = 0; c < columnCount; c++) {
                    String cellText = c < row.size() ? row.get(c) : "";
                    List<String> wrapped = wrap(cellText, bodyFont, BODY_FONT_SIZE, colWidth - 2 * TABLE_CELL_PADDING);
                    wrappedCells.add(wrapped);
                    lineCount = Math.max(lineCount, wrapped.size());
                }
                float rowHeight = lineCount * LINE_HEIGHT + 2 * TABLE_CELL_PADDING;
                ensureSpace(rowHeight);
                float rowTop = y;
                for (int c = 0; c < columnCount; c++) {
                    float cellX = MARGIN + c * colWidth;
                    List<String> lines = wrappedCells.get(c);
                    float lineY = rowTop - TABLE_CELL_PADDING - BODY_FONT_SIZE;
                    for (String line : lines) {
                        stream.beginText();
                        stream.setFont(bodyFont, BODY_FONT_SIZE);
                        stream.newLineAtOffset(cellX + TABLE_CELL_PADDING, lineY);
                        stream.showText(line);
                        stream.endText();
                        lineY -= LINE_HEIGHT;
                    }
                }
                drawTableGrid(rowTop, rowHeight, columnCount, colWidth);
                y = rowTop - rowHeight;
            }
            y -= LINE_HEIGHT / 2;
        }

        private void drawTableGrid(float rowTop, float rowHeight, int columnCount, float colWidth) throws IOException {
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, rowTop);
            stream.lineTo(MARGIN + columnCount * colWidth, rowTop);
            stream.stroke();
            stream.moveTo(MARGIN, rowTop - rowHeight);
            stream.lineTo(MARGIN + columnCount * colWidth, rowTop - rowHeight);
            stream.stroke();
            for (int c = 0; c <= columnCount; c++) {
                float x = MARGIN + c * colWidth;
                stream.moveTo(x, rowTop);
                stream.lineTo(x, rowTop - rowHeight);
                stream.stroke();
            }
        }

        private void text(PDFont font, float size, String value) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(value);
            stream.endText();
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < bottomMargin) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            pages.add(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - topMargin;
        }

        /** Ferme le flux courant puis rejoue l'en-tête/pied sur chaque page déjà créée (leur nombre n'est connu qu'une fois tout le contenu écrit). */
        void finish(String headerText, String footerText) throws IOException {
            stream.close();
            boolean hasHeader = headerText != null && !headerText.isBlank();
            boolean hasFooter = footerText != null && !footerText.isBlank();
            if (!hasHeader && !hasFooter) {
                return;
            }
            for (PDPage p : pages) {
                try (PDPageContentStream hf = new PDPageContentStream(document, p, PDPageContentStream.AppendMode.APPEND, true)) {
                    if (hasHeader) {
                        hf.beginText();
                        hf.setFont(bodyFont, HEADER_FOOTER_FONT_SIZE);
                        hf.newLineAtOffset(MARGIN, PDRectangle.A4.getHeight() - MARGIN + 6);
                        hf.showText(singleLine(headerText));
                        hf.endText();
                    }
                    if (hasFooter) {
                        hf.beginText();
                        hf.setFont(bodyFont, HEADER_FOOTER_FONT_SIZE);
                        hf.newLineAtOffset(MARGIN, MARGIN - LINE_HEIGHT);
                        hf.showText(singleLine(footerText));
                        hf.endText();
                    }
                }
            }
        }

        private String singleLine(String text) {
            return text.replaceAll("\\s*\\n\\s*", "  |  ").strip();
        }
    }

    private static List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isBlank()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && width(candidate, font, fontSize) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static float width(String text, PDFont font, float fontSize) {
        try {
            return font.getStringWidth(text) / 1000 * fontSize;
        } catch (IOException e) {
            return text.length() * fontSize * 0.5f;
        }
    }
}
