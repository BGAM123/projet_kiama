package com.docuai.extraction.html;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.VerticalAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Construit de vrais .docx avec Apache POI (plutôt que de figer des octets en
 * fixture) pour vérifier que la mise en forme réellement écrite par Word —
 * gras/italique/souligné/barré, couleur, police, taille, exposant/indice,
 * alignement, styles "Heading N", tableaux — ressort dans le HTML que
 * l'éditeur type Word sait ouvrir.
 */
class DocxHtmlImporterTest {

    private final DocxHtmlImporter importer = new DocxHtmlImporter();

    private byte[] toBytes(XWPFDocument document) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void toHtml_recognisesHeadingStyles_regardlessOfLocale() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Introduction");
            // Word FR nomme le même style "Titre 2" — les deux doivent être reconnus.
            XWPFParagraph frenchHeading = document.createParagraph();
            frenchHeading.setStyle("Titre2");
            frenchHeading.createRun().setText("Contexte");

            String html = importer.toHtml(toBytes(document));

            assertThat(html).contains("<h1>Introduction</h1>").contains("<h2>Contexte</h2>");
        }
    }

    @Test
    void toHtml_preservesCharacterFormatting() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun bold = paragraph.createRun();
            bold.setText("gras");
            bold.setBold(true);

            XWPFRun coloredRun = paragraph.createRun();
            coloredRun.setText("rouge");
            coloredRun.setColor("C00000");
            coloredRun.setFontFamily("Georgia");
            coloredRun.setFontSize(14);

            XWPFRun underlineStrike = paragraph.createRun();
            underlineStrike.setText("souligné-barré");
            underlineStrike.setUnderline(UnderlinePatterns.SINGLE);
            underlineStrike.setStrikeThrough(true);

            XWPFRun superscriptRun = paragraph.createRun();
            superscriptRun.setText("2");
            superscriptRun.setSubscript(VerticalAlign.SUPERSCRIPT);

            String html = importer.toHtml(toBytes(document));

            assertThat(html).contains("<strong>gras</strong>");
            assertThat(html).contains("color: #C00000;").contains("font-family: Georgia;").contains("rouge");
            assertThat(html).contains("<u>").contains("<s>").contains("souligné-barré");
            assertThat(html).contains("<sup>2</sup>");
        }
    }

    @Test
    void toHtml_readsParagraphAlignment() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph centered = document.createParagraph();
            centered.setAlignment(ParagraphAlignment.CENTER);
            centered.createRun().setText("Centré");

            String html = importer.toHtml(toBytes(document));

            assertThat(html).contains("style=\"text-align: center\">Centré");
        }
    }

    @Test
    void toHtml_rendersTheFirstTableRow_asHeaderCells() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Indicateur");
            table.getRow(0).getCell(1).setText("Valeur");
            table.getRow(1).getCell(0).setText("CA");
            table.getRow(1).getCell(1).setText("420k");

            String html = importer.toHtml(toBytes(document));

            assertThat(html).contains("<th><p>Indicateur</p></th>").contains("<td><p>CA</p></td>");
        }
    }

    @Test
    void toHtml_escapesHtmlSpecialCharacters_inText() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("<script>alert(1)</script> & Cie");

            String html = importer.toHtml(toBytes(document));

            assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;").contains("&amp; Cie");
        }
    }

    @Test
    void toHtml_returnsAnEmptyParagraph_forADocumentWithNoContent() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            assertThat(importer.toHtml(toBytes(document))).isEqualTo("<p></p>");
        }
    }

    @Test
    void toHtml_keepsBlankParagraphs_asIntentionalBlankLines() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Un");
            document.createParagraph(); // ligne blanche
            document.createParagraph().createRun().setText("Deux");

            String html = importer.toHtml(toBytes(document));

            assertThat(html).isEqualTo("<p>Un</p><p></p><p>Deux</p>");
        }
    }

    @Test
    void toHtml_throwsIllegalArgument_forUnreadableBytes() {
        assertThatThrownBy(() -> importer.toHtml("pas un docx".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
