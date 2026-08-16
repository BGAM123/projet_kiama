package com.docuai.export;

import com.docuai.export.DocumentBlocks.Block;
import com.docuai.export.DocumentBlocks.HeadingBlock;
import com.docuai.export.DocumentBlocks.ImageBlock;
import com.docuai.export.DocumentBlocks.ListBlock;
import com.docuai.export.DocumentBlocks.PageBreakBlock;
import com.docuai.export.DocumentBlocks.ParagraphBlock;
import com.docuai.export.DocumentBlocks.Segment;
import com.docuai.export.DocumentBlocks.TableBlock;
import com.docuai.export.DocumentBlocks.TableCell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce parseur est le contrat entre l'éditeur type Word du frontend et les
 * fichiers réellement livrés : tout ce que la barre d'outils sait produire doit
 * être reconnu ici, sinon la mise en forme choisie par l'utilisateur
 * disparaîtrait silencieusement du DOCX et du PDF.
 */
class HtmlContentParserTest {

    /** 1×1 px PNG transparent — le plus petit contenu d'image valide. */
    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    @Test
    void parse_readsHeadingsAndParagraphs_withTheirAlignment() {
        List<Block> blocks = HtmlContentParser.parse(
                "<h2 style=\"text-align: center\">Contexte</h2><p style=\"text-align: right\">Aligné à droite.</p>");

        assertThat(blocks).hasSize(2);
        HeadingBlock heading = (HeadingBlock) blocks.get(0);
        assertThat(heading.level()).isEqualTo(2);
        assertThat(DocumentBlocks.plainText(heading.segments())).isEqualTo("Contexte");
        assertThat(heading.align()).isEqualTo(DocumentBlocks.Align.CENTER);
        assertThat(((ParagraphBlock) blocks.get(1)).align()).isEqualTo(DocumentBlocks.Align.RIGHT);
    }

    @Test
    void parse_readsInlineMarks_includingUnderlineStrikeAndLinks() {
        ParagraphBlock paragraph = (ParagraphBlock) HtmlContentParser.parse(
                "<p><strong>gras</strong><em>ital</em><u>souligné</u><s>barré</s>"
                        + "<a href=\"https://x.io\">lien</a></p>").get(0);

        assertThat(paragraph.segments()).filteredOn(Segment::bold).extracting(Segment::text).containsExactly("gras");
        assertThat(paragraph.segments()).filteredOn(Segment::italic).extracting(Segment::text).containsExactly("ital");
        assertThat(paragraph.segments()).filteredOn(Segment::underline).extracting(Segment::text).containsExactly("souligné");
        assertThat(paragraph.segments()).filteredOn(Segment::strike).extracting(Segment::text).containsExactly("barré");
        assertThat(paragraph.segments()).filteredOn(s -> s.href() != null).extracting(Segment::href)
                .containsExactly("https://x.io");
    }

    /** Couleur, taille et police sont exactement ce que le pivot Markdown perdait — c'est la raison d'être de ce parseur. */
    @Test
    void parse_readsColorFontSizeAndFontFamily_fromInlineStyles() {
        ParagraphBlock paragraph = (ParagraphBlock) HtmlContentParser.parse(
                "<p><span style=\"color: #c00000; font-size: 24px; font-family: 'Times New Roman', serif\">Rouge</span></p>")
                .get(0);

        Segment segment = paragraph.segments().get(0);
        assertThat(segment.color()).isEqualTo("C00000");
        assertThat(segment.fontSize()).isEqualTo(18); // 24 px = 18 pt
        assertThat(segment.fontFamily()).isEqualTo("Times New Roman");
    }

    @Test
    void parse_readsRgbColors_asHexadecimal() {
        ParagraphBlock paragraph = (ParagraphBlock) HtmlContentParser.parse(
                "<p><span style=\"color: rgb(17, 85, 204)\">Bleu</span></p>").get(0);

        assertThat(paragraph.segments().get(0).color()).isEqualTo("1155CC");
    }

    /** Un style porté par un ancêtre doit teindre les paragraphes qu'il contient : l'éditeur produit ce balisage au collage. */
    @Test
    void parse_inheritsStylesFromAncestors() {
        ParagraphBlock paragraph = (ParagraphBlock) HtmlContentParser.parse(
                "<div style=\"color: #FF0000\"><p>Hérité</p></div>").get(0);

        assertThat(paragraph.segments().get(0).color()).isEqualTo("FF0000");
    }

    @Test
    void parse_readsTables_withHeaderCellsAndSpans() {
        TableBlock table = (TableBlock) HtmlContentParser.parse(
                "<table><tbody>"
                        + "<tr><th>Indicateur</th><th colspan=\"2\">Valeurs</th></tr>"
                        + "<tr><td rowspan=\"2\">CA</td><td>420k</td><td>510k</td></tr>"
                        + "</tbody></table>").get(0);

        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(0).cells()).extracting(TableCell::header).containsExactly(true, true);
        assertThat(table.rows().get(0).cells().get(1).colSpan()).isEqualTo(2);
        assertThat(table.rows().get(1).cells().get(0).rowSpan()).isEqualTo(2);
    }

    @Test
    void parse_readsNestedLists_withTheirDepth() {
        ListBlock list = (ListBlock) HtmlContentParser.parse(
                "<ul><li><p>Premier</p><ul><li><p>Imbriqué</p></li></ul></li><li><p>Second</p></li></ul>").get(0);

        assertThat(list.ordered()).isFalse();
        assertThat(list.items()).extracting(item -> DocumentBlocks.plainText(item.segments()))
                .containsExactly("Premier", "Imbriqué", "Second");
        assertThat(list.items()).extracting(DocumentBlocks.ListItem::depth).containsExactly(0, 1, 0);
    }

    @Test
    void parse_decodesInlineImages_andTheirRequestedWidth() {
        ImageBlock image = (ImageBlock) HtmlContentParser.parse(
                "<p><img src=\"data:image/png;base64," + PNG_1X1 + "\" style=\"width: 320px\"></p>").get(0);

        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.widthPx()).isEqualTo(320);
        assertThat(image.data()).isNotEmpty();
    }

    /** Une image hébergée ailleurs ne doit pas faire sortir une requête HTTP du serveur d'export. */
    @Test
    void parse_ignoresRemoteImages() {
        assertThat(HtmlContentParser.parse("<p><img src=\"https://ailleurs.example/logo.png\"></p>"))
                .noneMatch(ImageBlock.class::isInstance);
    }

    @Test
    void parse_recognisesPageBreaks() {
        assertThat(HtmlContentParser.parse("<p>Avant</p><div data-page-break></div><p>Après</p>"))
                .anyMatch(PageBreakBlock.class::isInstance);
    }

    /** Une ligne blanche voulue par l'utilisateur est un paragraphe vide, pas du vide à supprimer. */
    @Test
    void parse_keepsEmptyParagraphs() {
        assertThat(HtmlContentParser.parse("<p>Un</p><p></p><p>Deux</p>")).hasSize(3);
    }

    @Test
    void parse_mergesAdjacentSegments_thatShareTheSameFormatting() {
        ParagraphBlock paragraph = (ParagraphBlock) HtmlContentParser.parse(
                "<p><span>Un </span><span>seul </span><span>fragment</span></p>").get(0);

        assertThat(paragraph.segments()).hasSize(1);
        assertThat(paragraph.segments().get(0).text()).isEqualTo("Un seul fragment");
    }

    /**
     * Balisage tel que l'éditeur l'émet réellement, et non tel qu'on
     * l'écrirait à la main : `<colgroup>` avant le corps du tableau,
     * `colwidth` sur les cellules, et un `<mark>` dont le style porte aussi un
     * `color: inherit` qui ne doit surtout pas être lu comme une couleur de
     * texte.
     */
    @Test
    void parse_understandsTheMarkupTheEditorActuallyEmits() {
        List<Block> blocks = HtmlContentParser.parse("""
                <p style="text-align: center"><span style="font-family: Georgia">Georgia</span>\
                <mark data-color="#FFFF00" style="background-color: #FFFF00; color: inherit">surligné</mark></p>\
                <table style="min-width: 200px"><colgroup><col style="min-width: 100px"><col></colgroup>\
                <tbody><tr><th colwidth="100"><p>Clé</p></th><th><p>Valeur</p></th></tr>\
                <tr><td colspan="2" colwidth="200"><p>Fusionnée</p></td></tr></tbody></table>""");

        ParagraphBlock paragraph = (ParagraphBlock) blocks.get(0);
        assertThat(paragraph.align()).isEqualTo(DocumentBlocks.Align.CENTER);
        assertThat(paragraph.segments().get(0).fontFamily()).isEqualTo("Georgia");
        Segment highlighted = paragraph.segments().get(1);
        assertThat(highlighted.highlight()).isEqualTo("FFFF00");
        assertThat(highlighted.color()).as("`color: inherit` n'est pas une couleur de texte").isNull();

        TableBlock table = (TableBlock) blocks.get(1);
        assertThat(table.rows()).as("le colgroup n'ajoute pas de ligne").hasSize(2);
        assertThat(table.rows().get(1).cells()).singleElement()
                .extracting(TableCell::colSpan).isEqualTo(2);
    }

    @Test
    void parse_returnsNothing_forBlankInput() {
        assertThat(HtmlContentParser.parse("   ")).isEmpty();
        assertThat(HtmlContentParser.parse(null)).isEmpty();
    }
}
