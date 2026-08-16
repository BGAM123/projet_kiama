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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lit le HTML produit par l'éditeur type Word du frontend et le traduit en
 * {@link DocumentBlocks.Block} — c'est ce qui fait qu'une couleur, une taille
 * de police, un alignement, une image ou un tableau fusionné choisis dans
 * l'éditeur ressortent réellement dans le DOCX et le PDF, au lieu d'être perdus
 * comme ils l'étaient avec le format pivot Markdown.
 * <p>
 * Volontairement tolérant : une balise inconnue est traversée (ses enfants sont
 * traités), un attribut de style illisible est ignoré. Un export ne doit jamais
 * échouer parce que l'éditeur a émis un balisage inattendu — au pire une
 * décoration est perdue, jamais le texte.
 * <p>
 * Les images ne sont lues que depuis des URI {@code data:} : c'est ce que
 * produit l'éditeur (l'image est encodée dans le document lui-même), et cela
 * évite de faire sortir une requête HTTP du serveur d'export vers une URL
 * arbitraire présente dans le contenu.
 */
final class HtmlContentParser {

    private static final Logger log = LoggerFactory.getLogger(HtmlContentParser.class);

    private static final Pattern DATA_URI = Pattern.compile("^data:(?<mime>[^;,]+)(;charset=[^;,]+)?;base64,(?<data>.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CSS_LENGTH = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*(px|pt|em|rem|%)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RGB_COLOR = Pattern.compile(
            "rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,[^)]*)?\\)", Pattern.CASE_INSENSITIVE);

    /** 1 pt = 4/3 px en CSS — conversion utilisée dans les deux sens pour les tailles de police. */
    private static final double PX_PER_PT = 4d / 3d;

    private HtmlContentParser() {
    }

    static List<Block> parse(String html) {
        List<Block> blocks = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return blocks;
        }
        Element body = Jsoup.parseBodyFragment(html).body();
        for (Node node : body.childNodes()) {
            appendBlocks(node, blocks, Segment.plain(""));
        }
        return blocks;
    }

    /**
     * @param inherited fragment vide servant de porteur des marques héritées des
     *                  ancêtres (un {@code <div style="color:red">} colore les
     *                  paragraphes qu'il contient).
     */
    private static void appendBlocks(Node node, List<Block> blocks, Segment inherited) {
        if (node instanceof TextNode textNode) {
            // Texte nu entre deux blocs (rare, mais l'éditeur peut en produire
            // au collage) : promu en paragraphe plutôt que jeté.
            String text = normalise(textNode.getWholeText());
            if (!text.isBlank()) {
                blocks.add(new ParagraphBlock(List.of(inherited.withText(text)), null));
            }
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }

        String tag = element.tagName().toLowerCase(Locale.ROOT);
        Segment context = applyStyles(element, inherited);

        if (isPageBreak(element)) {
            blocks.add(new PageBreakBlock());
            return;
        }

        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                List<Segment> segments = inlineSegments(element, context);
                if (!isEmpty(segments)) {
                    blocks.add(new HeadingBlock(tag.charAt(1) - '0', segments, alignOf(element)));
                }
            }
            case "p", "div" -> appendParagraphOrChildren(element, blocks, context);
            case "ul", "ol" -> {
                List<ListItem> items = new ArrayList<>();
                // Un tableau collé dans un `<li>` ne peut pas être rendu à
                // l'intérieur d'un item : il est sorti après la liste plutôt
                // qu'aplati en texte.
                List<Block> trailing = new ArrayList<>();
                collectListItems(element, items, trailing, 0, context);
                if (!items.isEmpty()) {
                    blocks.add(new ListBlock("ol".equals(tag), items));
                }
                blocks.addAll(trailing);
            }
            case "table" -> {
                TableBlock table = parseTable(element, context);
                if (table != null) {
                    blocks.add(table);
                }
            }
            case "hr" -> blocks.add(new RuleBlock());
            case "img" -> {
                ImageBlock image = parseImage(element, alignOf(element));
                if (image != null) {
                    blocks.add(image);
                }
            }
            case "br" -> {
                // Un `<br>` hors paragraphe n'a rien à séparer.
            }
            case "figure", "blockquote", "section", "article", "main", "header", "footer" ->
                    element.childNodes().forEach(child -> appendBlocks(child, blocks, context));
            default -> {
                // Élément inline isolé au niveau bloc (`<span>`, `<strong>`…) :
                // traité comme un paragraphe pour ne pas perdre son texte.
                if (isBlockOnly(element)) {
                    element.childNodes().forEach(child -> appendBlocks(child, blocks, context));
                } else {
                    appendParagraphOrChildren(element, blocks, context);
                }
            }
        }
    }

    /**
     * Un {@code <div>}/{@code <p>} qui contient un tableau, une image de bloc ou
     * une liste ne peut pas devenir un paragraphe : ses enfants sont remontés au
     * niveau bloc, sinon le tableau serait aplati en texte.
     */
    private static void appendParagraphOrChildren(Element element, List<Block> blocks, Segment context) {
        boolean hasBlockChild = element.children().stream().anyMatch(HtmlContentParser::isBlockOnly);
        if (hasBlockChild) {
            element.childNodes().forEach(child -> appendBlocks(child, blocks, context));
            return;
        }

        Element loneImage = loneImageOf(element);
        if (loneImage != null) {
            ImageBlock image = parseImage(loneImage, alignOf(element));
            if (image != null) {
                blocks.add(image);
            }
            return;
        }

        List<Segment> segments = inlineSegments(element, context);
        // Un paragraphe vide est significatif dans un traitement de texte :
        // c'est une ligne blanche voulue par l'utilisateur, conservée telle
        // quelle plutôt que supprimée (contrairement au pivot Markdown, qui
        // n'avait aucun moyen de la représenter).
        blocks.add(new ParagraphBlock(segments, alignOf(element)));
    }

    /** Image seule dans son paragraphe (cas produit par l'éditeur) — rendue comme un bloc image, pas comme un paragraphe vide. */
    private static Element loneImageOf(Element element) {
        Element image = null;
        for (Node child : element.childNodes()) {
            if (child instanceof TextNode text) {
                if (!text.getWholeText().isBlank()) {
                    return null;
                }
            } else if (child instanceof Element childElement) {
                if (!"img".equals(childElement.tagName().toLowerCase(Locale.ROOT)) || image != null) {
                    return null;
                }
                image = childElement;
            }
        }
        return image;
    }

    private static boolean isNestedList(Element element) {
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        return "ul".equals(tag) || "ol".equals(tag);
    }

    private static boolean isBlockOnly(Element element) {
        return switch (element.tagName().toLowerCase(Locale.ROOT)) {
            case "table", "ul", "ol", "hr", "h1", "h2", "h3", "h4", "h5", "h6", "figure", "blockquote" -> true;
            default -> false;
        };
    }

    private static boolean isPageBreak(Element element) {
        return element.hasClass("page-break")
                || element.hasAttr("data-page-break")
                || "page-break".equals(element.attr("data-type"))
                || style(element, "break-after") != null
                || style(element, "page-break-after") != null;
    }

    // -----------------------------------------------------------------------
    // Listes
    // -----------------------------------------------------------------------

    private static void collectListItems(Element list, List<ListItem> items, List<Block> trailing,
                                          int depth, Segment inherited) {
        for (Element item : list.children()) {
            if (!"li".equals(item.tagName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Segment context = applyStyles(item, inherited);
            List<Segment> segments = new ArrayList<>();
            List<Element> nestedLists = new ArrayList<>();
            for (Node child : item.childNodes()) {
                if (child instanceof Element childElement && isNestedList(childElement)) {
                    nestedLists.add(childElement);
                } else if (child instanceof Element childElement && isBlockOnly(childElement)) {
                    appendBlocks(childElement, trailing, context);
                } else {
                    collectInline(child, segments, context);
                }
            }
            if (!isEmpty(segments)) {
                items.add(new ListItem(depth, merge(segments)));
            }
            for (Element nested : nestedLists) {
                collectListItems(nested, items, trailing, depth + 1, context);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Tableaux
    // -----------------------------------------------------------------------

    private static TableBlock parseTable(Element table, Segment inherited) {
        List<TableRow> rows = new ArrayList<>();
        for (Element row : table.select("tr")) {
            List<TableCell> cells = new ArrayList<>();
            for (Element cell : row.children()) {
                String tag = cell.tagName().toLowerCase(Locale.ROOT);
                if (!"td".equals(tag) && !"th".equals(tag)) {
                    continue;
                }
                Segment context = applyStyles(cell, inherited);
                cells.add(new TableCell(
                        merge(inlineSegments(cell, context)),
                        "th".equals(tag),
                        positiveInt(cell.attr("colspan"), 1),
                        positiveInt(cell.attr("rowspan"), 1),
                        alignOf(cell)));
            }
            if (!cells.isEmpty()) {
                rows.add(new TableRow(cells));
            }
        }
        return rows.isEmpty() ? null : new TableBlock(rows);
    }

    // -----------------------------------------------------------------------
    // Images
    // -----------------------------------------------------------------------

    private static ImageBlock parseImage(Element img, Align align) {
        Matcher matcher = DATA_URI.matcher(img.attr("src").trim());
        if (!matcher.matches()) {
            log.debug("Image ignorée à l'export : source non intégrée au document ({}).",
                    abbreviate(img.attr("src")));
            return null;
        }
        try {
            byte[] data = Base64.getDecoder().decode(matcher.group("data").replaceAll("\\s", ""));
            return new ImageBlock(data, matcher.group("mime").toLowerCase(Locale.ROOT), imageWidth(img), align);
        } catch (IllegalArgumentException e) {
            log.warn("Image ignorée à l'export : contenu base64 illisible ({}).", e.getMessage());
            return null;
        }
    }

    private static Integer imageWidth(Element img) {
        Integer fromStyle = pixels(style(img, "width"));
        if (fromStyle != null) {
            return fromStyle;
        }
        return pixels(img.attr("width"));
    }

    // -----------------------------------------------------------------------
    // Inline
    // -----------------------------------------------------------------------

    private static List<Segment> inlineSegments(Element element, Segment context) {
        List<Segment> segments = new ArrayList<>();
        for (Node child : element.childNodes()) {
            collectInline(child, segments, context);
        }
        return merge(segments);
    }

    private static void collectInline(Node node, List<Segment> segments, Segment context) {
        if (node instanceof TextNode textNode) {
            String text = normalise(textNode.getWholeText());
            if (!text.isEmpty()) {
                segments.add(context.withText(text));
            }
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }

        String tag = element.tagName().toLowerCase(Locale.ROOT);
        if ("br".equals(tag)) {
            segments.add(context.withText("\n"));
            return;
        }
        if ("img".equals(tag)) {
            // Une image au milieu d'un paragraphe n'a pas de rendu inline dans
            // les exporteurs (POI/PDFBox placent une image sur sa propre
            // ligne) : ignorée ici, elle reste rendue quand elle constitue son
            // propre paragraphe (cas de l'éditeur).
            return;
        }

        Segment nested = applyStyles(element, switch (tag) {
            case "strong", "b" -> context.bold(true);
            case "em", "i" -> context.italic(true);
            case "u", "ins" -> context.underline(true);
            case "s", "del", "strike" -> context.strike(true);
            case "code", "kbd", "samp" -> context.code(true);
            case "sup" -> context.asSuperscript();
            case "sub" -> context.asSubscript();
            // `<mark>` sans couleur explicite : jaune, comme le surligneur par défaut de Word.
            case "mark" -> context.highlight(context.highlight() != null ? context.highlight() : "FFFF00");
            case "a" -> context.href(element.attr("href").isBlank() ? null : element.attr("href").trim());
            default -> context;
        });
        for (Node child : element.childNodes()) {
            collectInline(child, segments, nested);
        }
    }

    /** Fusionne les fragments consécutifs de même mise en forme — un `<span>` par mot produirait autant de runs Word inutiles. */
    private static List<Segment> merge(List<Segment> segments) {
        List<Segment> merged = new ArrayList<>();
        for (Segment segment : segments) {
            if (segment.text().isEmpty()) {
                continue;
            }
            if (!merged.isEmpty()) {
                Segment previous = merged.get(merged.size() - 1);
                if (sameFormatting(previous, segment)) {
                    merged.set(merged.size() - 1, previous.withText(previous.text() + segment.text()));
                    continue;
                }
            }
            merged.add(segment);
        }
        return merged;
    }

    private static boolean sameFormatting(Segment a, Segment b) {
        return a.bold() == b.bold() && a.italic() == b.italic() && a.underline() == b.underline()
                && a.strike() == b.strike() && a.code() == b.code()
                && a.superscript() == b.superscript() && a.subscript() == b.subscript()
                && equal(a.href(), b.href()) && equal(a.color(), b.color()) && equal(a.highlight(), b.highlight())
                && equal(a.fontSize(), b.fontSize()) && equal(a.fontFamily(), b.fontFamily());
    }

    // -----------------------------------------------------------------------
    // Styles CSS
    // -----------------------------------------------------------------------

    /**
     * Reporte sur le fragment les styles inline portés par l'élément. Seules les
     * propriétés réellement présentes écrasent l'héritage : un {@code <span>}
     * qui ne fixe qu'une couleur ne doit pas réinitialiser la taille de police
     * héritée de son parent.
     */
    private static Segment applyStyles(Element element, Segment base) {
        Segment result = base;

        String color = hexColor(style(element, "color"));
        if (color != null) {
            result = result.color(color);
        }
        String highlight = hexColor(firstNonNull(style(element, "background-color"), style(element, "background")));
        if (highlight != null) {
            result = result.highlight(highlight);
        }
        Integer fontSize = points(style(element, "font-size"));
        if (fontSize != null) {
            result = result.fontSize(fontSize);
        }
        String fontFamily = firstFontFamily(style(element, "font-family"));
        if (fontFamily != null) {
            result = result.fontFamily(fontFamily);
        }

        String weight = style(element, "font-weight");
        if (weight != null && isBoldWeight(weight)) {
            result = result.bold(true);
        }
        if ("italic".equalsIgnoreCase(String.valueOf(style(element, "font-style")))) {
            result = result.italic(true);
        }
        String decoration = style(element, "text-decoration");
        if (decoration != null) {
            String lower = decoration.toLowerCase(Locale.ROOT);
            if (lower.contains("underline")) {
                result = result.underline(true);
            }
            if (lower.contains("line-through")) {
                result = result.strike(true);
            }
        }
        return result;
    }

    private static Align alignOf(Element element) {
        Align fromStyle = Align.from(style(element, "text-align"));
        return fromStyle != null ? fromStyle : Align.from(element.attr("align"));
    }

    /** Valeur d'une propriété dans l'attribut {@code style} de l'élément, {@code null} si absente. */
    private static String style(Element element, String property) {
        String style = element.attr("style");
        if (style.isBlank()) {
            return null;
        }
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon < 0) {
                continue;
            }
            if (declaration.substring(0, colon).trim().equalsIgnoreCase(property)) {
                String value = declaration.substring(colon + 1).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /** Couleur CSS -> hexadécimal sans `#` (convention OOXML). Les noms de couleurs CSS ne sont pas résolus : l'éditeur émet toujours `#rrggbb` ou `rgb(...)`. */
    private static String hexColor(String css) {
        if (css == null) {
            return null;
        }
        String value = css.trim();
        if (value.startsWith("#")) {
            String hex = value.substring(1);
            if (hex.length() == 3) {
                StringBuilder expanded = new StringBuilder();
                for (char c : hex.toCharArray()) {
                    expanded.append(c).append(c);
                }
                hex = expanded.toString();
            }
            return hex.length() >= 6 ? hex.substring(0, 6).toUpperCase(Locale.ROOT) : null;
        }
        Matcher rgb = RGB_COLOR.matcher(value);
        if (rgb.find()) {
            return String.format("%02X%02X%02X",
                    clampByte(rgb.group(1)), clampByte(rgb.group(2)), clampByte(rgb.group(3)));
        }
        return null;
    }

    private static int clampByte(String value) {
        return Math.max(0, Math.min(255, Integer.parseInt(value)));
    }

    private static Integer pixels(String css) {
        Double value = cssLength(css, 16d);
        return value == null ? null : (int) Math.round(value);
    }

    private static Integer points(String css) {
        Double px = cssLength(css, 16d);
        if (px == null) {
            return null;
        }
        int pt = (int) Math.round(px / PX_PER_PT);
        return pt <= 0 ? null : pt;
    }

    /** Longueur CSS -> pixels. {@code em}/{@code rem} sont résolus sur {@code base}, les pourcentages ignorés (pas de contexte pour les résoudre). */
    private static Double cssLength(String css, double base) {
        if (css == null) {
            return null;
        }
        Matcher matcher = CSS_LENGTH.matcher(css);
        if (!matcher.matches()) {
            return null;
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2) == null ? "px" : matcher.group(2).toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "px" -> value;
            case "pt" -> value * PX_PER_PT;
            case "em", "rem" -> value * base;
            default -> null;
        };
    }

    /** `font-family: "Times New Roman", serif` -> `Times New Roman` : Word veut une police nommée, pas une pile de repli. */
    private static String firstFontFamily(String css) {
        if (css == null) {
            return null;
        }
        String first = css.split(",")[0].trim().replaceAll("^[\"']|[\"']$", "").trim();
        return first.isEmpty() ? null : first;
    }

    private static boolean isBoldWeight(String weight) {
        String value = weight.trim().toLowerCase(Locale.ROOT);
        if ("bold".equals(value) || "bolder".equals(value)) {
            return true;
        }
        try {
            return Integer.parseInt(value) >= 600;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Utilitaires
    // -----------------------------------------------------------------------

    /** Espaces HTML repliés comme le ferait un navigateur, insécables normalisés — sinon ils ressortent en `?` dans le PDF. */
    private static String normalise(String text) {
        return text.replace(' ', ' ').replaceAll("[\\t\\r\\n]+", " ").replaceAll(" {2,}", " ");
    }

    private static boolean isEmpty(List<Segment> segments) {
        return segments.stream().allMatch(s -> s.text().isBlank());
    }

    private static int positiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static <T> boolean equal(T a, T b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static String abbreviate(String value) {
        return value.length() <= 40 ? value : value.substring(0, 40) + "…";
    }
}
