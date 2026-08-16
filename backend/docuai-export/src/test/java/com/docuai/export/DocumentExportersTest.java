package com.docuai.export;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que le HTML de l'éditeur type Word produit des fichiers réellement
 * ouvrables — la mise en forme est validée par
 * {@link HtmlContentParserTest} au niveau des blocs, ce test-ci garantit que
 * l'écriture OOXML/PDF derrière ne casse pas (fusion de cellules, image
 * intégrée, saut de page sont les points les plus délicats).
 */
class DocumentExportersTest {

    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private static final String RICH_HTML = """
            <h1>Rapport annuel</h1>
            <p style="text-align: justify"><span style="color: #C00000; font-size: 18px">Introduction</span>
            en <strong>gras</strong>, <em>italique</em>, <u>souligné</u> et H<sub>2</sub>O.</p>
            <ul><li><p>Premier</p><ul><li><p>Imbriqué</p></li></ul></li></ul>
            <table><tbody>
              <tr><th>Poste</th><th colspan="2">Montants</th></tr>
              <tr><td rowspan="2">Ventes</td><td>420k</td><td>510k</td></tr>
              <tr><td>380k</td><td>495k</td></tr>
            </tbody></table>
            <p><img src="data:image/png;base64,%s" style="width: 200px"></p>
            <div data-page-break></div>
            <h2 style="text-align: center">Annexes</h2>
            """.formatted(PNG_1X1);

    private static ExportContent content() {
        return new ExportContent("Rapport annuel", null, RICH_HTML, "En-tête", "Pied de page");
    }

    @Test
    void docx_writesAnOpenableDocument_withARealTableAndAnEmbeddedImage() throws Exception {
        ExportedFile file = new DocxDocumentExporter().export(content());

        assertThat(file.filename()).endsWith(".docx");
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.content()))) {
            assertThat(document.getTables()).hasSize(1);
            assertThat(document.getAllPictures()).hasSize(1);

            XWPFTable table = document.getTables().get(0);
            assertThat(table.getRows()).hasSize(3);
            // La cellule "Montants" couvre deux colonnes : la cellule couverte
            // est retirée, sa largeur portée par gridSpan.
            assertThat(table.getRow(0).getTableCells()).hasSize(2);
            assertThat(table.getRow(0).getCell(1).getCTTc().getTcPr().getGridSpan().getVal())
                    .isEqualTo(BigInteger.valueOf(2));
            // "Ventes" fusionne deux lignes : la ligne suivante garde une
            // cellule de continuation à la même colonne.
            assertThat(table.getRow(1).getCell(0).getCTTc().getTcPr().getVMerge()).isNotNull();
        }
    }

    @Test
    void pdf_writesAnOpenableDocument_withThePageBreakHonoured() throws Exception {
        ExportedFile file = new PdfDocumentExporter().export(content());

        assertThat(file.filename()).endsWith(".pdf");
        try (PDDocument document = Loader.loadPDF(file.content())) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void markdown_serialisesTheHtmlBackToText() {
        ExportedFile file = new MarkdownDocumentExporter().export(content());

        String markdown = new String(file.content(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(markdown).contains("# Rapport annuel")
                .contains("**gras**")
                .contains("| Poste |")
                .contains("- Premier")
                .contains("  - Imbriqué");
    }

    /**
     * Balisage capturé tel quel dans l'éditeur en fonctionnement, avec ses
     * particularités : tableau enveloppé dans un {@code div.tableWrapper},
     * couleurs sérialisées en {@code rgb()} plutôt qu'en hexadécimal, espaces
     * insécables, et {@code <br>} de fin de paragraphe vide. Écrire ce test à
     * partir d'un HTML idéalisé passerait à côté de chacun de ces cas.
     */
    private static final String EDITOR_OUTPUT = """
            <h1 style="text-align: center;">Rapport</h1>\
            <div data-page-break="" class="page-break" contenteditable="false"></div>\
            <p>Du <strong>gras</strong>, <span style="color: rgb(192, 0, 0);">de la couleur</span> et \
            <mark data-color="#FFFF00" style="background-color: rgb(255, 255, 0); color: inherit;">du surlignage</mark>.</p>\
            <p style="text-align: justify;"><span style="font-family: Georgia; font-size: 14px;">Georgia 14&nbsp;px.</span></p>\
            <div class="tableWrapper"><table style="min-width: 75px;">\
            <colgroup><col style="min-width: 25px;"><col style="min-width: 25px;"></colgroup>\
            <tbody><tr><th colspan="1" rowspan="1"><p>Indicateur</p></th><th colspan="1" rowspan="1"><p>2025</p></th></tr>\
            <tr><td colspan="1" rowspan="1"><p>Marge</p></td><td colspan="1" rowspan="1"><p>15 %</p></td></tr></tbody></table></div>\
            <p><br class="ProseMirror-trailingBreak"></p>""";

    @Test
    void editorOutput_survivesTheRoundTrip_downToTheDocxTable() throws Exception {
        ExportContent content = new ExportContent("Rapport", null, EDITOR_OUTPUT, null, null);

        // Le tableau ne doit pas être aplati par le div qui l'enveloppe.
        assertThat(content.blocks()).anyMatch(DocumentBlocks.TableBlock.class::isInstance);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(new DocxDocumentExporter().export(content).content()))) {
            assertThat(document.getTables()).hasSize(1);
            assertThat(document.getTables().get(0).getRow(0).getCell(0).getText()).isEqualTo("Indicateur");
            // rgb(192, 0, 0) reconnu comme une vraie couleur de texte.
            assertThat(document.getParagraphs())
                    .flatExtracting(XWPFParagraph::getRuns)
                    .anyMatch(run -> "C00000".equals(run.getColor()));
        }
        assertThat(new PdfDocumentExporter().export(content).content()).isNotEmpty();
    }

    /** Les documents antérieurs à l'éditeur type Word n'ont que du Markdown : ils doivent rester exportables. */
    @Test
    void legacyMarkdownContent_isStillExported() throws Exception {
        ExportContent legacy = new ExportContent("Ancien", "# Titre\n\nUn paragraphe **important**.", null, null);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(new DocxDocumentExporter().export(legacy).content()))) {
            assertThat(document.getParagraphs()).anyMatch(p -> p.getText().contains("important"));
        }
    }
}
