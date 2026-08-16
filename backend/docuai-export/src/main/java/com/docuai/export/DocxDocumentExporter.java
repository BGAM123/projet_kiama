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
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.VerticalAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHighlight;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;

/**
 * Export DOCX : traduit les {@link DocumentBlocks.Block} produits par
 * l'éditeur type Word (HTML) ou par l'assemblage Markdown hérité en un vrai
 * document Word — titres avec niveau de plan reconnu par le volet de
 * navigation, tableaux {@link XWPFTable} avec cellules fusionnées, images
 * intégrées, alignements, couleurs et polices, en-tête/pied de page statiques.
 */
@Component
public class DocxDocumentExporter implements DocumentExporter {

    private static final Logger log = LoggerFactory.getLogger(DocxDocumentExporter.class);

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int[] HEADING_FONT_SIZE = {20, 16, 14, 13, 12, 11};
    /** En twips (1/20 de point) — ~0,6 cm par niveau d'imbrication. */
    private static final int LIST_INDENT_PER_LEVEL = 360;
    /** Largeur utile d'une page A4 avec les marges Word par défaut (2,54 cm), en pixels CSS à 96 dpi. */
    private static final int PAGE_WIDTH_PX = 624;
    private static final int DEFAULT_IMAGE_WIDTH_PX = 480;

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
            titleRun.setText(request.resolvedTitle());
            titleRun.setBold(true);
            titleRun.setFontSize(22);

            for (Block block : request.blocks()) {
                writeBlock(document, block);
            }

            document.write(out);
            return new ExportedFile(out.toByteArray(), ExportFilenames.build(request.title(), "docx"), CONTENT_TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Échec de génération du document DOCX.", e);
        }
    }

    private void writeBlock(XWPFDocument document, Block block) {
        if (block instanceof HeadingBlock heading) {
            writeHeading(document, heading);
        } else if (block instanceof TableBlock table) {
            writeTable(document, table);
        } else if (block instanceof ParagraphBlock paragraph) {
            XWPFParagraph target = document.createParagraph();
            applyAlignment(target, paragraph.align());
            writeSegments(target, paragraph.segments());
        } else if (block instanceof ListBlock list) {
            writeList(document, list);
        } else if (block instanceof ImageBlock image) {
            writeImage(document, image);
        } else if (block instanceof RuleBlock) {
            writeHorizontalRule(document);
        } else if (block instanceof PageBreakBlock) {
            document.createParagraph().createRun().addBreak(BreakType.PAGE);
        }
    }

    private void writeHeading(XWPFDocument document, HeadingBlock heading) {
        int level = Math.max(1, Math.min(heading.level(), 6));
        XWPFParagraph paragraph = document.createParagraph();
        applyAlignment(paragraph, heading.align());
        // Niveau de plan natif (w:outlineLvl) : Word reconnaît la ligne comme
        // un titre (volet de navigation, table des matières) même sans style
        // "HeadingN" défini dans le document (un XWPFDocument vierge n'a pas
        // de styles.xml) — plus robuste qu'un setStyle() qui pointerait vers
        // un style inexistant.
        CTPPr ppr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTDecimalNumber outlineLvl = ppr.addNewOutlineLvl();
        outlineLvl.setVal(BigInteger.valueOf(level - 1));

        writeSegments(paragraph, heading.segments(), true, HEADING_FONT_SIZE[level - 1]);
    }

    private void writeSegments(XWPFParagraph paragraph, List<Segment> segments) {
        writeSegments(paragraph, segments, false, null);
    }

    /**
     * Un run Word par fragment de mise en forme — c'est ce qui évite qu'un gras
     * ou une couleur choisis dans l'éditeur ressortent en balisage brut. Les
     * liens sont rendus en bleu souligné plutôt qu'en vrai champ HYPERLINK :
     * l'apparence attendue sans la plomberie de relations OOXML.
     *
     * @param forceBold        titres et cellules d'en-tête, en gras quel que soit le fragment
     * @param defaultFontSize  taille appliquée aux fragments qui n'en fixent pas (titres), {@code null} pour laisser la taille par défaut du document
     */
    private void writeSegments(XWPFParagraph paragraph, List<Segment> segments, boolean forceBold, Integer defaultFontSize) {
        for (Segment segment : segments) {
            XWPFRun run = paragraph.createRun();
            writeText(run, segment.text());
            run.setBold(forceBold || segment.bold());
            run.setItalic(segment.italic());
            if (segment.underline() || segment.href() != null) {
                run.setUnderline(UnderlinePatterns.SINGLE);
            }
            if (segment.strike()) {
                run.setStrikeThrough(true);
            }
            if (segment.code()) {
                run.setFontFamily("Consolas");
            } else if (segment.fontFamily() != null) {
                run.setFontFamily(segment.fontFamily());
            }
            if (segment.fontSize() != null) {
                run.setFontSize(segment.fontSize());
            } else if (defaultFontSize != null) {
                run.setFontSize(defaultFontSize);
            }
            if (segment.color() != null) {
                run.setColor(segment.color());
            } else if (segment.href() != null) {
                run.setColor("1155CC");
            }
            if (segment.highlight() != null) {
                applyHighlight(run, segment.highlight());
            }
            if (segment.superscript()) {
                run.setSubscript(VerticalAlign.SUPERSCRIPT);
            } else if (segment.subscript()) {
                run.setSubscript(VerticalAlign.SUBSCRIPT);
            }
        }
    }

    /** Les retours à la ligne internes d'un fragment (issus d'un {@code <br>}) doivent devenir de vrais sauts de ligne Word, pas des espaces. */
    private void writeText(XWPFRun run, String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lines[i], i);
        }
    }

    /**
     * Word n'accepte qu'une palette fermée de couleurs de surlignage
     * (w:highlight) : la couleur demandée est ramenée à la plus proche de cette
     * palette. Un fond arbitraire ressortirait sinon sans aucun surlignage.
     */
    private void applyHighlight(XWPFRun run, String hex) {
        CTHighlight highlight = run.getCTR().isSetRPr()
                ? run.getCTR().getRPr().addNewHighlight()
                : run.getCTR().addNewRPr().addNewHighlight();
        highlight.setVal(nearestHighlight(hex));
    }

    private STHighlightColor.Enum nearestHighlight(String hex) {
        int rgb;
        try {
            rgb = Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return STHighlightColor.YELLOW;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        STHighlightColor.Enum best = STHighlightColor.YELLOW;
        long bestDistance = Long.MAX_VALUE;
        for (Object[] candidate : HIGHLIGHT_PALETTE) {
            int[] color = (int[]) candidate[1];
            long distance = squared(r - color[0]) + squared(g - color[1]) + squared(b - color[2]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = (STHighlightColor.Enum) candidate[0];
            }
        }
        return best;
    }

    private static long squared(int value) {
        return (long) value * value;
    }

    private static final Object[][] HIGHLIGHT_PALETTE = {
            {STHighlightColor.YELLOW, new int[]{255, 255, 0}},
            {STHighlightColor.GREEN, new int[]{0, 255, 0}},
            {STHighlightColor.CYAN, new int[]{0, 255, 255}},
            {STHighlightColor.MAGENTA, new int[]{255, 0, 255}},
            {STHighlightColor.BLUE, new int[]{0, 0, 255}},
            {STHighlightColor.RED, new int[]{255, 0, 0}},
            {STHighlightColor.DARK_BLUE, new int[]{0, 0, 139}},
            {STHighlightColor.DARK_CYAN, new int[]{0, 139, 139}},
            {STHighlightColor.DARK_GREEN, new int[]{0, 100, 0}},
            {STHighlightColor.DARK_MAGENTA, new int[]{139, 0, 139}},
            {STHighlightColor.DARK_RED, new int[]{139, 0, 0}},
            {STHighlightColor.DARK_YELLOW, new int[]{128, 128, 0}},
            {STHighlightColor.DARK_GRAY, new int[]{169, 169, 169}},
            {STHighlightColor.LIGHT_GRAY, new int[]{211, 211, 211}},
            {STHighlightColor.WHITE, new int[]{255, 255, 255}},
            {STHighlightColor.BLACK, new int[]{0, 0, 0}},
    };

    private void applyAlignment(XWPFParagraph paragraph, Align align) {
        if (align == null) {
            return;
        }
        paragraph.setAlignment(switch (align) {
            case CENTER -> ParagraphAlignment.CENTER;
            case RIGHT -> ParagraphAlignment.RIGHT;
            case JUSTIFY -> ParagraphAlignment.BOTH;
            case LEFT -> ParagraphAlignment.LEFT;
        });
    }

    /**
     * Puces et numéros écrits en texte, avec un retrait proportionnel au
     * niveau : un XWPFDocument vierge n'embarque pas de numbering.xml, et en
     * créer un pour trois niveaux de liste coûterait plus qu'il ne rapporte.
     * La numérotation repart à 1 à chaque changement de niveau, comme dans un
     * traitement de texte.
     */
    private void writeList(XWPFDocument document, ListBlock list) {
        int[] counters = new int[16];
        int previousDepth = 0;
        for (ListItem item : list.items()) {
            int depth = Math.max(0, Math.min(item.depth(), counters.length - 1));
            if (depth > previousDepth) {
                counters[depth] = 0;
            }
            counters[depth]++;
            previousDepth = depth;

            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(LIST_INDENT_PER_LEVEL * (depth + 1));
            paragraph.createRun().setText(list.ordered() ? counters[depth] + ". " : "• ");
            writeSegments(paragraph, item.segments());
        }
    }

    private void writeHorizontalRule(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setBorderBottom(Borders.SINGLE);
    }

    private void writeImage(XWPFDocument document, ImageBlock image) {
        int format = pictureFormat(image.contentType());
        if (format == 0) {
            log.warn("Image ignorée à l'export DOCX : format {} non pris en charge par Word.", image.contentType());
            return;
        }
        int widthPx = Math.min(image.widthPx() == null ? DEFAULT_IMAGE_WIDTH_PX : image.widthPx(), PAGE_WIDTH_PX);
        int heightPx = scaledHeight(image.data(), widthPx);

        XWPFParagraph paragraph = document.createParagraph();
        applyAlignment(paragraph, image.align() == null ? Align.CENTER : image.align());
        try (ByteArrayInputStream in = new ByteArrayInputStream(image.data())) {
            paragraph.createRun().addPicture(in, format, "image", Units.pixelToEMU(widthPx), Units.pixelToEMU(heightPx));
        } catch (Exception e) {
            // Une image illisible ne doit pas faire échouer tout l'export : le
            // reste du document reste livrable.
            log.warn("Image ignorée à l'export DOCX : {}", e.getMessage());
        }
    }

    /** Hauteur déduite du ratio réel de l'image — sans quoi une image redimensionnée en largeur serait déformée. */
    private int scaledHeight(byte[] data, int widthPx) {
        try {
            java.awt.image.BufferedImage source = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
            if (source != null && source.getWidth() > 0) {
                return Math.max(1, Math.round((float) widthPx * source.getHeight() / source.getWidth()));
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Ratio d'image indéterminable, hauteur par défaut appliquée : {}", e.getMessage());
        }
        return Math.round(widthPx * 0.75f);
    }

    private int pictureFormat(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> Document.PICTURE_TYPE_PNG;
            case "image/jpeg", "image/jpg" -> Document.PICTURE_TYPE_JPEG;
            case "image/gif" -> Document.PICTURE_TYPE_GIF;
            case "image/bmp" -> Document.PICTURE_TYPE_BMP;
            case "image/tiff" -> Document.PICTURE_TYPE_TIFF;
            default -> 0;
        };
    }

    /**
     * Les cellules fusionnées sont écrites en OOXML natif ({@code w:gridSpan}
     * pour l'horizontal, {@code w:vMerge} pour le vertical) : la grille reste
     * rectangulaire côté XML, seules les cellules couvertes sont marquées comme
     * continuation de leur voisine.
     */
    private void writeTable(XWPFDocument document, TableBlock tableBlock) {
        List<TableRow> rows = tableBlock.rows();
        if (rows.isEmpty()) {
            return;
        }
        int columnCount = rows.stream().mapToInt(row -> row.cells().stream().mapToInt(TableCell::colSpan).sum())
                .max().orElse(0);
        if (columnCount == 0) {
            return;
        }

        XWPFTable table = document.createTable(rows.size(), columnCount);
        // `true` = la cellule de cette colonne/ligne est déjà couverte par une
        // fusion venue d'au-dessus ou de la gauche.
        boolean[][] covered = new boolean[rows.size()][columnCount];

        for (int r = 0; r < rows.size(); r++) {
            int column = 0;
            for (TableCell cell : rows.get(r).cells()) {
                while (column < columnCount && covered[r][column]) {
                    column++;
                }
                if (column >= columnCount) {
                    break;
                }
                int colSpan = Math.min(cell.colSpan(), columnCount - column);
                int rowSpan = Math.min(cell.rowSpan(), rows.size() - r);

                writeCell(table, r, column, cell, colSpan, rowSpan > 1 ? STMerge.RESTART : null);
                for (int rr = r; rr < r + rowSpan; rr++) {
                    for (int cc = column; cc < column + colSpan; cc++) {
                        if (rr != r || cc != column) {
                            covered[rr][cc] = true;
                        }
                    }
                }
                // Les cellules couvertes verticalement restent présentes dans
                // le XML mais marquées "continue" — c'est ainsi que Word rend
                // une fusion verticale.
                for (int rr = r + 1; rr < r + rowSpan; rr++) {
                    writeCell(table, rr, column, new TableCell(List.of(), cell.header(), colSpan, 1, cell.align()),
                            colSpan, STMerge.CONTINUE);
                }
                column += colSpan;
            }
        }

        // Les cellules réellement couvertes horizontalement sont retirées :
        // gridSpan porte déjà leur largeur.
        for (int r = rows.size() - 1; r >= 0; r--) {
            for (int c = columnCount - 1; c >= 0; c--) {
                if (covered[r][c] && !isMergeContinuation(table, r, c)) {
                    table.getRow(r).getCtRow().removeTc(c);
                }
            }
        }
    }

    private boolean isMergeContinuation(XWPFTable table, int row, int column) {
        var tc = table.getRow(row).getCell(column);
        if (tc == null || !tc.getCTTc().isSetTcPr() || !tc.getCTTc().getTcPr().isSetVMerge()) {
            return false;
        }
        return STMerge.CONTINUE.equals(tc.getCTTc().getTcPr().getVMerge().getVal());
    }

    private void writeCell(XWPFTable table, int row, int column, TableCell cell, int colSpan, STMerge.Enum vMerge) {
        var target = table.getRow(row).getCell(column);
        if (target == null) {
            return;
        }
        var properties = target.getCTTc().isSetTcPr() ? target.getCTTc().getTcPr() : target.getCTTc().addNewTcPr();
        if (colSpan > 1) {
            properties.addNewGridSpan().setVal(BigInteger.valueOf(colSpan));
        }
        if (vMerge != null) {
            properties.addNewVMerge().setVal(vMerge);
        }
        if (cell.header()) {
            properties.addNewShd().setFill("F2F2F2");
        }

        target.removeParagraph(0);
        XWPFParagraph paragraph = target.addParagraph();
        applyAlignment(paragraph, cell.align());
        writeSegments(paragraph, cell.segments(), cell.header(), null);
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
