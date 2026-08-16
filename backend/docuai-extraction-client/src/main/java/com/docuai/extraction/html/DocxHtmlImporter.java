package com.docuai.extraction.html;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.officeDocument.x2006.sharedTypes.STVerticalAlignRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convertit un .docx en HTML directement éditable dans l'éditeur type Word du
 * frontend — c'est ce qui permet à un utilisateur d'importer un document
 * existant et de continuer à le rédiger dans l'application, mise en forme
 * comprise, plutôt que de repartir d'une page blanche.
 * <p>
 * Contrairement à {@code StructureExtractionService} (qui ne retient que le
 * texte, pour construire le squelette d'un Document Type), ce convertisseur
 * lit les {@link XWPFRun} : gras, italique, souligné, barré, couleur, police,
 * taille, exposant/indice, surlignage, alignement de paragraphe, tableaux et
 * images intégrées (converties en {@code data:} URI, cohérent avec ce que
 * produit l'éditeur lui-même). Le balisage émis est volontairement celui que
 * {@code HtmlContentParser} (docuai-export) et l'éditeur TipTap savent déjà
 * lire — mêmes classes de test que côté export, pas un format parallèle.
 */
@Service
public class DocxHtmlImporter {

    private static final Logger log = LoggerFactory.getLogger(DocxHtmlImporter.class);

    /** Même convention que {@code StructureExtractionService} — styles Word "Heading N"/"Titre N" — mais sans plafond à 3 : l'éditeur va jusqu'à h6. */
    private static final Pattern HEADING_STYLE = Pattern.compile("(?i)^(?:heading|titre)\\s*([1-6])");

    private static final Map<STHighlightColor.Enum, String> HIGHLIGHT_HEX = Map.ofEntries(
            Map.entry(STHighlightColor.YELLOW, "FFFF00"),
            Map.entry(STHighlightColor.GREEN, "00FF00"),
            Map.entry(STHighlightColor.CYAN, "00FFFF"),
            Map.entry(STHighlightColor.MAGENTA, "FF00FF"),
            Map.entry(STHighlightColor.BLUE, "0000FF"),
            Map.entry(STHighlightColor.RED, "FF0000"),
            Map.entry(STHighlightColor.DARK_BLUE, "00008B"),
            Map.entry(STHighlightColor.DARK_CYAN, "008B8B"),
            Map.entry(STHighlightColor.DARK_GREEN, "006400"),
            Map.entry(STHighlightColor.DARK_MAGENTA, "8B008B"),
            Map.entry(STHighlightColor.DARK_RED, "8B0000"),
            Map.entry(STHighlightColor.DARK_YELLOW, "808000"),
            Map.entry(STHighlightColor.DARK_GRAY, "A9A9A9"),
            Map.entry(STHighlightColor.LIGHT_GRAY, "D3D3D3"),
            Map.entry(STHighlightColor.WHITE, "FFFFFF"),
            Map.entry(STHighlightColor.BLACK, "000000"));

    /**
     * @param content octets bruts du fichier .docx
     * @return HTML prêt à être ouvert dans l'éditeur — jamais {@code null} (un
     *         document sans contenu détecté donne un paragraphe vide).
     */
    public String toHtml(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            List<String> blocks = new ArrayList<>();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(blocks, paragraph);
                } else if (element instanceof XWPFTable table) {
                    blocks.add(tableHtml(table));
                }
            }
            return blocks.isEmpty() ? "<p></p>" : String.join("", blocks);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Le fichier .docx n'a pas pu être lu — il est peut-être corrompu ou protégé.", e);
        }
    }

    // -----------------------------------------------------------------------
    // Paragraphes
    // -----------------------------------------------------------------------

    /**
     * Émet un bloc {@code <hN>}/{@code <p>} par paragraphe source. Une image
     * intégrée coupe le flux inline en son propre bloc {@code <p><img></p>} —
     * l'éditeur (comme {@code HtmlContentParser} à l'export) ne rend pas une
     * image au milieu d'un paragraphe de texte.
     */
    private void appendParagraph(List<String> blocks, XWPFParagraph paragraph) {
        Integer level = headingLevelOf(paragraph);
        String tag = level != null ? "h" + level : "p";
        String styleAttr = alignAttributeOf(paragraph);
        StringBuilder buffer = new StringBuilder();
        boolean hasText = false;
        boolean emittedImage = false;

        for (XWPFRun run : paragraph.getRuns()) {
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                if (hasText) {
                    blocks.add(wrap(tag, styleAttr, buffer.toString()));
                    buffer.setLength(0);
                    hasText = false;
                }
                String image = imageHtml(picture);
                if (image != null) {
                    blocks.add(image);
                    emittedImage = true;
                }
                // La suite du paragraphe (texte après l'image, rare) redevient
                // un paragraphe normal : un titre ne doit pas se scinder en
                // deux "hN".
                tag = "p";
            }
            String text = run.text();
            if (text != null && !text.isEmpty()) {
                buffer.append(runHtml(run, text));
                if (!text.isBlank()) {
                    hasText = true;
                }
            }
        }

        // Titre sans texte ni image : ignoré (même convention que
        // StructureExtractionService). Paragraphe normal vide : conservé,
        // c'est une ligne blanche voulue par l'auteur — sauf juste après une
        // image déjà émise, où ce ne serait qu'un doublon.
        if (hasText || (level == null && !emittedImage)) {
            blocks.add(wrap(tag, styleAttr, buffer.toString()));
        }
    }

    private String wrap(String tag, String styleAttr, String innerHtml) {
        return "<" + tag + (styleAttr != null ? " style=\"" + styleAttr + "\"" : "") + ">" + innerHtml + "</" + tag + ">";
    }

    private Integer headingLevelOf(XWPFParagraph paragraph) {
        String styleId = paragraph.getStyleID();
        if (styleId == null) {
            return null;
        }
        String styleName = styleId;
        XWPFDocument document = paragraph.getDocument();
        if (document != null && document.getStyles() != null) {
            XWPFStyle style = document.getStyles().getStyle(styleId);
            if (style != null && style.getName() != null) {
                styleName = style.getName();
            }
        }
        Matcher matcher = HEADING_STYLE.matcher(styleName);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    /** {@code LEFT}/{@code START} est la valeur par défaut de Word : ne pas l'écrire évite d'alourdir chaque paragraphe d'un style inutile. */
    private String alignAttributeOf(XWPFParagraph paragraph) {
        ParagraphAlignment alignment = paragraph.getAlignment();
        if (alignment == null) {
            return null;
        }
        return switch (alignment) {
            case CENTER -> "text-align: center";
            case RIGHT, END -> "text-align: right";
            case BOTH -> "text-align: justify";
            default -> null;
        };
    }

    // -----------------------------------------------------------------------
    // Runs (mise en forme caractère)
    // -----------------------------------------------------------------------

    private String runHtml(XWPFRun run, String text) {
        String escaped = escape(text)
                .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")
                .replace("\n", "<br>");

        StringBuilder style = new StringBuilder();
        if (run.getColor() != null && !"auto".equalsIgnoreCase(run.getColor())) {
            style.append("color: #").append(run.getColor()).append(';');
        }
        String highlight = highlightHexOf(run);
        if (highlight != null) {
            style.append("background-color: #").append(highlight).append(';');
        }
        if (run.getFontFamily() != null) {
            style.append("font-family: ").append(run.getFontFamily()).append(';');
        }
        Double fontSize = run.getFontSizeAsDouble();
        if (fontSize != null && fontSize > 0) {
            style.append("font-size: ").append(Math.round(fontSize * Units.EMU_PER_POINT / (double) Units.EMU_PER_PIXEL))
                    .append("px;");
        }

        String html = escaped;
        if (style.length() > 0) {
            html = "<span style=\"" + style + "\">" + html + "</span>";
        }
        STVerticalAlignRun.Enum vertical = run.getVerticalAlignment();
        if (vertical == STVerticalAlignRun.SUPERSCRIPT) {
            html = "<sup>" + html + "</sup>";
        } else if (vertical == STVerticalAlignRun.SUBSCRIPT) {
            html = "<sub>" + html + "</sub>";
        }
        if (run.getUnderline() != null && run.getUnderline() != UnderlinePatterns.NONE) {
            html = "<u>" + html + "</u>";
        }
        if (run.isStrikeThrough() || run.isStrike()) {
            html = "<s>" + html + "</s>";
        }
        if (run.isItalic()) {
            html = "<em>" + html + "</em>";
        }
        if (run.isBold()) {
            html = "<strong>" + html + "</strong>";
        }
        return html;
    }

    private String highlightHexOf(XWPFRun run) {
        STHighlightColor.Enum highlight = run.getTextHighlightColor();
        if (highlight == null || highlight == STHighlightColor.NONE) {
            return null;
        }
        return HIGHLIGHT_HEX.get(highlight);
    }

    // -----------------------------------------------------------------------
    // Tableaux
    // -----------------------------------------------------------------------

    /** Comme dans l'éditeur : la première ligne devient l'en-tête ({@code <th>}), le reste des cellules de données. */
    private String tableHtml(XWPFTable table) {
        StringBuilder html = new StringBuilder("<table><tbody>");
        List<XWPFTableRow> rows = table.getRows();
        for (int r = 0; r < rows.size(); r++) {
            String cellTag = r == 0 ? "th" : "td";
            html.append("<tr>");
            for (XWPFTableCell cell : rows.get(r).getTableCells()) {
                html.append('<').append(cellTag).append("><p>").append(cellHtml(cell)).append("</p></").append(cellTag).append('>');
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    /** Les paragraphes internes d'une cellule sont aplatis en une seule ligne (séparés par un saut de ligne) : le modèle de cellule de l'éditeur est un texte, pas un sous-document. */
    private String cellHtml(XWPFTableCell cell) {
        StringBuilder html = new StringBuilder();
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            if (!html.isEmpty()) {
                html.append("<br>");
            }
            for (XWPFRun run : paragraph.getRuns()) {
                String text = run.text();
                if (text != null && !text.isEmpty()) {
                    html.append(runHtml(run, text));
                }
            }
        }
        return html.toString();
    }

    // -----------------------------------------------------------------------
    // Images
    // -----------------------------------------------------------------------

    /** Convertie en {@code data:} URI, exactement comme le fait l'éditeur à l'insertion — {@code HtmlContentParser} sait déjà la relire à l'export. */
    private String imageHtml(XWPFPicture picture) {
        XWPFPictureData data = picture.getPictureData();
        if (data == null) {
            return null;
        }
        String mimeType = mimeTypeOf(data);
        if (mimeType == null) {
            log.warn("Image ignorée à l'import : format {} non pris en charge par le navigateur.", data.suggestFileExtension());
            return null;
        }
        Integer widthPx = widthPxOf(picture);
        String base64 = Base64.getEncoder().encodeToString(data.getData());
        String style = widthPx != null ? " style=\"width: " + widthPx + "px\"" : "";
        return "<p><img src=\"data:" + mimeType + ";base64," + base64 + "\"" + style + "></p>";
    }

    private String mimeTypeOf(XWPFPictureData data) {
        return switch (data.suggestFileExtension().toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "tiff", "tif" -> "image/tiff";
            case "webp" -> "image/webp";
            default -> null;
        };
    }

    /** Largeur DrawingML (EMU) déclarée sur l'ancrage de l'image dans le document — absente, l'éditeur retombe sur la taille naturelle de l'image. */
    private Integer widthPxOf(XWPFPicture picture) {
        try {
            var shapeProperties = picture.getCTPicture().getSpPr();
            if (shapeProperties == null || !shapeProperties.isSetXfrm()) {
                return null;
            }
            var transform = shapeProperties.getXfrm();
            if (!transform.isSetExt()) {
                return null;
            }
            long emuWidth = transform.getExt().getCx();
            return emuWidth > 0 ? (int) Math.round(emuWidth / (double) Units.EMU_PER_PIXEL) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
