package com.docuai.export;

import com.docuai.export.DocumentBlocks.Align;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Export PDF des {@link DocumentBlocks.Block} : titres, paragraphes alignés,
 * listes, tableaux dessinés en grille (PDFBox n'a pas d'API tableau native
 * contrairement à Apache POI), images intégrées, sauts de page, en-tête/pied de
 * page répétés sur chaque page (ajoutés en second passage une fois la
 * pagination connue).
 * <p>
 * Limite assumée : les polices utilisées sont les 14 polices standard PDF
 * (Helvetica / Times / Courier). Une police choisie dans l'éditeur est ramenée à
 * la plus proche des trois familles — intégrer un fichier de police par nom
 * demandé supposerait de les embarquer toutes, ce que le DOCX (qui, lui, garde
 * le nom exact) rend inutile.
 */
@Component
public class PdfDocumentExporter implements DocumentExporter {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentExporter.class);

    private static final float MARGIN = 50f;
    private static final float TITLE_FONT_SIZE = 18f;
    private static final float BODY_FONT_SIZE = 11f;
    /** Interligne exprimé en multiple de la taille de police, pour que le texte agrandi ne se chevauche pas. */
    private static final float LINE_SPACING = 1.36f;
    private static final float PARAGRAPH_SPACING = 7f;
    private static final float[] HEADING_FONT_SIZE = {18f, 15f, 13f, 12f, 11f, 11f};
    private static final float HEADER_FOOTER_FONT_SIZE = 9f;
    private static final float TABLE_CELL_PADDING = 4f;
    private static final float LIST_INDENT_PER_LEVEL = 16f;
    private static final float SCRIPT_SCALE = 0.66f;
    /** 1 pt = 1/72 pouce, les pixels CSS sont à 96 dpi. */
    private static final float PT_PER_PX = 72f / 96f;

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
            cursor.writeTitle(request.resolvedTitle());

            for (Block block : request.blocks()) {
                if (block instanceof HeadingBlock heading) {
                    cursor.writeHeading(heading);
                } else if (block instanceof ParagraphBlock paragraph) {
                    cursor.writeParagraph(paragraph);
                } else if (block instanceof TableBlock table) {
                    cursor.writeTable(table);
                } else if (block instanceof ListBlock list) {
                    cursor.writeList(list);
                } else if (block instanceof ImageBlock image) {
                    cursor.writeImage(image);
                } else if (block instanceof RuleBlock) {
                    cursor.writeHorizontalRule();
                } else if (block instanceof PageBreakBlock) {
                    cursor.pageBreak();
                }
            }
            cursor.finish(request.headerText(), request.footerText());

            document.save(out);
            return new ExportedFile(out.toByteArray(), ExportFilenames.build(request.title(), "pdf"), "application/pdf");
        } catch (IOException e) {
            throw new IllegalStateException("Échec de génération du document PDF.", e);
        }
    }

    /** Fragment de ligne prêt à être dessiné : texte homogène, police et taille résolues. */
    private record Piece(String text, PDFont font, float size, Color color, Color highlight,
                          boolean underline, boolean strike, float baselineShift) {
    }

    /** Ligne mise en page : ses fragments et sa largeur totale, connue une fois la coupure faite. */
    private record Line(List<Piece> pieces, float width, float height) {
    }

    /** Curseur d'écriture mutable — encapsule la pagination pour éviter de faire circuler page/stream/y entre des méthodes statiques. */
    private static final class Cursor {
        private final PDDocument document;
        private final float pageWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;
        private final float topMargin;
        private final float bottomMargin;
        private final List<PDPage> pages = new ArrayList<>();
        private PDPageContentStream stream;
        private float y;

        Cursor(PDDocument document, boolean hasHeader, boolean hasFooter) throws IOException {
            this.document = document;
            this.topMargin = MARGIN + (hasHeader ? BODY_FONT_SIZE * LINE_SPACING + 6 : 0);
            this.bottomMargin = MARGIN + (hasFooter ? BODY_FONT_SIZE * LINE_SPACING + 6 : 0);
            newPage();
        }

        void writeTitle(String title) throws IOException {
            List<Segment> segments = List.of(Segment.plain(title).bold(true).fontSize(Math.round(TITLE_FONT_SIZE)));
            draw(layout(segments, pageWidth, TITLE_FONT_SIZE), 0f, Align.LEFT, pageWidth);
            y -= PARAGRAPH_SPACING * 2;
        }

        void writeHeading(HeadingBlock heading) throws IOException {
            float size = HEADING_FONT_SIZE[Math.max(1, Math.min(heading.level(), 6)) - 1];
            y -= PARAGRAPH_SPACING;
            List<Segment> bolded = heading.segments().stream().map(s -> s.bold(true)).toList();
            draw(layout(bolded, pageWidth, size), 0f, heading.align(), pageWidth);
            y -= PARAGRAPH_SPACING;
        }

        void writeParagraph(ParagraphBlock paragraph) throws IOException {
            List<Line> lines = layout(paragraph.segments(), pageWidth, BODY_FONT_SIZE);
            if (lines.isEmpty()) {
                // Paragraphe vide : une ligne blanche voulue par l'utilisateur.
                ensureSpace(BODY_FONT_SIZE * LINE_SPACING);
                y -= BODY_FONT_SIZE * LINE_SPACING;
                return;
            }
            draw(lines, 0f, paragraph.align(), pageWidth);
            y -= PARAGRAPH_SPACING;
        }

        void writeList(ListBlock list) throws IOException {
            int[] counters = new int[16];
            int previousDepth = 0;
            for (ListItem item : list.items()) {
                int depth = Math.max(0, Math.min(item.depth(), counters.length - 1));
                if (depth > previousDepth) {
                    counters[depth] = 0;
                }
                counters[depth]++;
                previousDepth = depth;

                float indent = LIST_INDENT_PER_LEVEL * (depth + 1);
                String prefix = list.ordered() ? counters[depth] + ". " : "• ";
                List<Segment> segments = new ArrayList<>();
                segments.add(Segment.plain(prefix));
                segments.addAll(item.segments());
                draw(layout(segments, pageWidth - indent, BODY_FONT_SIZE), indent, Align.LEFT, pageWidth - indent);
            }
            y -= PARAGRAPH_SPACING;
        }

        void writeHorizontalRule() throws IOException {
            ensureSpace(BODY_FONT_SIZE * LINE_SPACING);
            stream.setStrokingColor(Color.DARK_GRAY);
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(MARGIN + pageWidth, y);
            stream.stroke();
            y -= BODY_FONT_SIZE * LINE_SPACING;
        }

        void pageBreak() throws IOException {
            newPage();
        }

        void writeImage(ImageBlock image) throws IOException {
            PDImageXObject pdImage;
            try {
                pdImage = PDImageXObject.createFromByteArray(document, image.data(), "image");
            } catch (IOException | RuntimeException e) {
                log.warn("Image ignorée à l'export PDF : {}", e.getMessage());
                return;
            }
            float width = image.widthPx() != null ? image.widthPx() * PT_PER_PX : pdImage.getWidth() * PT_PER_PX;
            width = Math.min(width, pageWidth);
            float height = width * pdImage.getHeight() / Math.max(1f, pdImage.getWidth());

            // Une image plus haute que la zone utile est réduite plutôt que
            // coupée en deux pages : PDFBox ne sait pas fractionner un dessin.
            float usableHeight = PDRectangle.A4.getHeight() - topMargin - bottomMargin;
            if (height > usableHeight) {
                width *= usableHeight / height;
                height = usableHeight;
            }
            ensureSpace(height + PARAGRAPH_SPACING);

            float x = MARGIN + offsetFor(image.align() == null ? Align.CENTER : image.align(), width, pageWidth);
            stream.drawImage(pdImage, x, y - height, width, height);
            y -= height + PARAGRAPH_SPACING;
        }

        /**
         * Grille dessinée cellule par cellule. Les fusions ({@code colSpan}/{@code
         * rowSpan}) ne sont pas rendues : PDFBox n'a pas de modèle de tableau, et
         * reproduire une fusion demanderait de suivre la géométrie de toute la
         * grille. Le DOCX, lui, les rend fidèlement — c'est le format à privilégier
         * pour un tableau complexe.
         */
        void writeTable(TableBlock tableBlock) throws IOException {
            List<TableRow> rows = tableBlock.rows();
            if (rows.isEmpty()) {
                return;
            }
            int columnCount = rows.stream().mapToInt(row -> row.cells().size()).max().orElse(0);
            if (columnCount == 0) {
                return;
            }
            float colWidth = pageWidth / columnCount;
            float textWidth = colWidth - 2 * TABLE_CELL_PADDING;

            for (TableRow row : rows) {
                List<List<Line>> cellLines = new ArrayList<>();
                float contentHeight = BODY_FONT_SIZE * LINE_SPACING;
                for (int c = 0; c < columnCount; c++) {
                    List<Segment> segments = c < row.cells().size() ? row.cells().get(c).segments() : List.of();
                    List<Line> lines = layout(segments, textWidth, BODY_FONT_SIZE);
                    cellLines.add(lines);
                    contentHeight = Math.max(contentHeight, totalHeight(lines));
                }
                float rowHeight = contentHeight + 2 * TABLE_CELL_PADDING;
                ensureSpace(rowHeight);

                float rowTop = y;
                for (int c = 0; c < columnCount; c++) {
                    TableCell cell = c < row.cells().size() ? row.cells().get(c) : null;
                    if (cell != null && cell.header()) {
                        stream.setNonStrokingColor(new Color(0xF2, 0xF2, 0xF2));
                        stream.addRect(MARGIN + c * colWidth, rowTop - rowHeight, colWidth, rowHeight);
                        stream.fill();
                        stream.setNonStrokingColor(Color.BLACK);
                    }
                    y = rowTop - TABLE_CELL_PADDING;
                    Align align = cell == null ? null : cell.align();
                    drawInPlace(cellLines.get(c), MARGIN + c * colWidth + TABLE_CELL_PADDING, align, textWidth);
                }
                drawTableGrid(rowTop, rowHeight, columnCount, colWidth);
                y = rowTop - rowHeight;
            }
            y -= PARAGRAPH_SPACING;
        }

        private void drawTableGrid(float rowTop, float rowHeight, int columnCount, float colWidth) throws IOException {
            stream.setStrokingColor(Color.GRAY);
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
            stream.setStrokingColor(Color.BLACK);
        }

        // -------------------------------------------------------------------
        // Mise en page du texte
        // -------------------------------------------------------------------

        /**
         * Coupe une suite de fragments en lignes tenant dans {@code maxWidth}.
         * PDFBox n'ayant pas de notion de flux de texte, la largeur est mesurée
         * fragment par fragment — c'est le prix d'un gras qui ressort réellement
         * en gras, d'une couleur réellement colorée et d'une taille respectée.
         */
        private List<Line> layout(List<Segment> segments, float maxWidth, float defaultSize) {
            List<Line> lines = new ArrayList<>();
            List<Piece> current = new ArrayList<>();
            float currentWidth = 0f;
            float currentHeight = 0f;

            for (Segment segment : segments) {
                float size = fontSize(segment, defaultSize);
                PDFont font = fontFor(segment);
                float lineHeight = size * LINE_SPACING;
                // `\n` = saut de ligne explicite (issu d'un <br>) : il ferme la
                // ligne courante sans attendre la coupure au mot.
                String[] hardLines = sanitize(segment.text(), font).split("\n", -1);
                for (int h = 0; h < hardLines.length; h++) {
                    if (h > 0) {
                        lines.add(new Line(current, currentWidth, Math.max(currentHeight, lineHeight)));
                        current = new ArrayList<>();
                        currentWidth = 0f;
                        currentHeight = 0f;
                    }
                    String[] words = hardLines[h].split(" ", -1);
                    for (int i = 0; i < words.length; i++) {
                        String piece = i > 0 ? " " + words[i] : words[i];
                        if (piece.isEmpty()) {
                            continue;
                        }
                        float pieceWidth = width(piece, font, size);
                        if (!current.isEmpty() && currentWidth + pieceWidth > maxWidth) {
                            lines.add(new Line(current, currentWidth, Math.max(currentHeight, lineHeight)));
                            current = new ArrayList<>();
                            currentWidth = 0f;
                            currentHeight = 0f;
                            piece = words[i]; // pas d'espace en début de ligne
                            if (piece.isEmpty()) {
                                continue;
                            }
                            pieceWidth = width(piece, font, size);
                        }
                        current.add(new Piece(piece, font, size, color(segment), highlight(segment),
                                segment.underline() || segment.href() != null, segment.strike(),
                                baselineShift(segment, size)));
                        currentWidth += pieceWidth;
                        currentHeight = Math.max(currentHeight, lineHeight);
                    }
                }
            }
            if (!current.isEmpty()) {
                lines.add(new Line(current, currentWidth, currentHeight));
            }
            return lines;
        }

        private float totalHeight(List<Line> lines) {
            float total = 0f;
            for (Line line : lines) {
                total += line.height();
            }
            return total;
        }

        /** Dessine en gérant les sauts de page (contenu de flux). */
        private void draw(List<Line> lines, float indent, Align align, float maxWidth) throws IOException {
            for (Line line : lines) {
                ensureSpace(line.height());
                drawLine(line, MARGIN + indent + offsetFor(align, line.width(), maxWidth));
                y -= line.height();
            }
        }

        /** Dessine sans pagination — pour un contenu déjà borné (cellule de tableau), dont la hauteur a été réservée en amont. */
        private void drawInPlace(List<Line> lines, float x, Align align, float maxWidth) throws IOException {
            for (Line line : lines) {
                drawLine(line, x + offsetFor(align, line.width(), maxWidth));
                y -= line.height();
            }
        }

        private float offsetFor(Align align, float contentWidth, float maxWidth) {
            if (align == null || contentWidth >= maxWidth) {
                return 0f;
            }
            // JUSTIFY n'étire pas les espaces (il faudrait repasser sur chaque
            // ligne pour répartir le résidu) : rendu comme un alignement à
            // gauche, ce que fait déjà tout lecteur devant un texte non justifié.
            return switch (align) {
                case CENTER -> (maxWidth - contentWidth) / 2f;
                case RIGHT -> maxWidth - contentWidth;
                case LEFT, JUSTIFY -> 0f;
            };
        }

        private void drawLine(Line line, float startX) throws IOException {
            float x = startX;
            for (Piece piece : line.pieces()) {
                float pieceWidth = width(piece.text(), piece.font(), piece.size());
                float baseline = y - piece.size() + piece.baselineShift();

                if (piece.highlight() != null) {
                    stream.setNonStrokingColor(piece.highlight());
                    stream.addRect(x, baseline - piece.size() * 0.25f, pieceWidth, piece.size() * 1.2f);
                    stream.fill();
                }
                stream.setNonStrokingColor(piece.color());
                stream.beginText();
                stream.setFont(piece.font(), piece.size());
                stream.newLineAtOffset(x, baseline);
                stream.showText(piece.text());
                stream.endText();

                if (piece.underline() || piece.strike()) {
                    stream.setStrokingColor(piece.color());
                    stream.setLineWidth(Math.max(0.4f, piece.size() / 22f));
                    if (piece.underline()) {
                        stream.moveTo(x, baseline - piece.size() * 0.14f);
                        stream.lineTo(x + pieceWidth, baseline - piece.size() * 0.14f);
                        stream.stroke();
                    }
                    if (piece.strike()) {
                        stream.moveTo(x, baseline + piece.size() * 0.28f);
                        stream.lineTo(x + pieceWidth, baseline + piece.size() * 0.28f);
                        stream.stroke();
                    }
                    stream.setStrokingColor(Color.BLACK);
                }
                x += pieceWidth;
            }
            stream.setNonStrokingColor(Color.BLACK);
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
            PDPage page = new PDPage(PDRectangle.A4);
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
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (PDPage p : pages) {
                try (PDPageContentStream hf = new PDPageContentStream(document, p, PDPageContentStream.AppendMode.APPEND, true)) {
                    if (hasHeader) {
                        writeBand(hf, font, singleLine(headerText), PDRectangle.A4.getHeight() - MARGIN + 6);
                    }
                    if (hasFooter) {
                        writeBand(hf, font, singleLine(footerText), MARGIN - HEADER_FOOTER_FONT_SIZE * LINE_SPACING);
                    }
                }
            }
        }

        private void writeBand(PDPageContentStream target, PDFont font, String text, float atY) throws IOException {
            target.beginText();
            target.setFont(font, HEADER_FOOTER_FONT_SIZE);
            target.newLineAtOffset(MARGIN, atY);
            target.showText(sanitize(text, font));
            target.endText();
        }

        private String singleLine(String text) {
            return text.replaceAll("\\s*\\n\\s*", "  |  ").strip();
        }
    }

    // -----------------------------------------------------------------------
    // Résolution des polices et couleurs
    // -----------------------------------------------------------------------

    private static float fontSize(Segment segment, float defaultSize) {
        float size = segment.fontSize() != null ? segment.fontSize() : defaultSize;
        return segment.superscript() || segment.subscript() ? size * SCRIPT_SCALE : size;
    }

    private static float baselineShift(Segment segment, float size) {
        if (segment.superscript()) {
            return size * 0.45f;
        }
        return segment.subscript() ? -size * 0.2f : 0f;
    }

    private static Color color(Segment segment) {
        if (segment.color() != null) {
            Color parsed = parseHex(segment.color());
            if (parsed != null) {
                return parsed;
            }
        }
        return segment.href() != null ? new Color(0x11, 0x55, 0xCC) : Color.BLACK;
    }

    private static Color highlight(Segment segment) {
        return segment.highlight() == null ? null : parseHex(segment.highlight());
    }

    private static Color parseHex(String hex) {
        try {
            return new Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Famille demandée ramenée à l'une des trois familles standard PDF, puis
     * variante grasse/italique. Une police inconnue tombe sur Helvetica : le
     * texte reste lisible, seule la forme des lettres diffère.
     */
    private static PDFont fontFor(Segment segment) {
        boolean bold = segment.bold();
        boolean italic = segment.italic();
        if (segment.code() || isMonospace(segment.fontFamily())) {
            return new PDType1Font(bold
                    ? (italic ? Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE : Standard14Fonts.FontName.COURIER_BOLD)
                    : (italic ? Standard14Fonts.FontName.COURIER_OBLIQUE : Standard14Fonts.FontName.COURIER));
        }
        if (isSerif(segment.fontFamily())) {
            return new PDType1Font(bold
                    ? (italic ? Standard14Fonts.FontName.TIMES_BOLD_ITALIC : Standard14Fonts.FontName.TIMES_BOLD)
                    : (italic ? Standard14Fonts.FontName.TIMES_ITALIC : Standard14Fonts.FontName.TIMES_ROMAN));
        }
        return new PDType1Font(bold
                ? (italic ? Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE : Standard14Fonts.FontName.HELVETICA_BOLD)
                : (italic ? Standard14Fonts.FontName.HELVETICA_OBLIQUE : Standard14Fonts.FontName.HELVETICA));
    }

    private static boolean isMonospace(String family) {
        if (family == null) {
            return false;
        }
        String value = family.toLowerCase(Locale.ROOT);
        return value.contains("courier") || value.contains("consolas") || value.contains("mono");
    }

    private static boolean isSerif(String family) {
        if (family == null) {
            return false;
        }
        String value = family.toLowerCase(Locale.ROOT);
        return value.contains("times") || value.contains("georgia") || value.contains("garamond")
                || value.contains("cambria") || value.contains("book") || value.equals("serif");
    }

    /**
     * Les 14 polices standard PDF n'encodent que WinAnsi : un caractère hors
     * de ce jeu (flèche, emoji, alphabet non latin) ferait échouer
     * {@code showText} et donc tout l'export. Il est remplacé par « ? » — un
     * caractère perdu vaut mieux qu'un document perdu.
     */
    private static String sanitize(String text, PDFont font) {
        try {
            font.getStringWidth(text);
            return text;
        } catch (IOException | IllegalArgumentException e) {
            StringBuilder sb = new StringBuilder(text.length());
            for (char c : text.toCharArray()) {
                if (c == '\n') {
                    sb.append(c);
                    continue;
                }
                try {
                    font.getStringWidth(String.valueOf(c));
                    sb.append(c);
                } catch (IOException | IllegalArgumentException ignored) {
                    sb.append('?');
                }
            }
            return sb.toString();
        }
    }

    private static float width(String text, PDFont font, float fontSize) {
        try {
            return font.getStringWidth(text) / 1000 * fontSize;
        } catch (IOException | IllegalArgumentException e) {
            return text.length() * fontSize * 0.5f;
        }
    }
}
