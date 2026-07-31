package com.docuai.extraction.structure;

import com.docuai.core.model.StructureNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construit l'arbre de structure (titres/paragraphes/tableaux, à plat — le
 * seed applicatif V2__seed_roles_permissions.sql montre un exemple de
 * Document Type avec une structure entièrement plate, sans imbrication ;
 * l'éditeur manuel côté frontend permet d'imbriquer des sous-sections après
 * coup si besoin) à partir du fichier source importé.
 * <p>
 * Seul le DOCX bénéficie d'une vraie détection structurelle (styles de
 * paragraphe "Heading N"/"Titre N", tableaux, ordre réel du document via
 * Apache POI). Le PDF (PDFBox n'expose aucune sémantique de titre native),
 * le texte brut/Markdown et le repli legacy .doc (non couvert par
 * poi-ooxml, qui ne lit que l'OOXML) sont découpés en paragraphes plats —
 * limitation assumée pour cette itération du Bloc 4, corrigible plus tard
 * (heuristique de taille de police pour le PDF, poi-scratchpad pour .doc).
 */
@Service
public class StructureExtractionService {

    private static final Pattern HEADING_STYLE = Pattern.compile("(?i)^(?:heading|titre)\\s*([1-6])");
    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final int MAX_TABLE_COLUMNS = 12;
    private static final int PARAGRAPH_LABEL_MAX_LENGTH = 200;

    /**
     * @param content         octets bruts du fichier source
     * @param extension       extension en minuscules (docx, pdf, md, txt, doc)
     * @param fallbackRawText texte déjà extrait par Tika (TextExtractionService),
     *                        utilisé comme repli pour les formats sans parseur
     *                        structurel dédié (.doc legacy binaire)
     */
    public List<StructureNode> extract(byte[] content, String extension, String fallbackRawText) {
        try {
            return switch (extension == null ? "" : extension.toLowerCase()) {
                case "docx" -> extractDocx(content);
                case "pdf" -> extractPdf(content);
                case "md" -> extractMarkdown(new String(content, StandardCharsets.UTF_8));
                case "txt" -> extractPlainText(new String(content, StandardCharsets.UTF_8));
                case "doc" -> extractPlainText(fallbackRawText);
                default -> throw new StructureExtractionException("Format non supporté pour l'extraction de structure : " + extension);
            };
        } catch (StructureExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new StructureExtractionException("Échec de l'extraction de la structure du document.", e);
        }
    }

    private List<StructureNode> extractDocx(byte[] content) throws IOException {
        List<StructureNode> nodes = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(1);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    addParagraphNode(nodes, counter, paragraph);
                } else if (element instanceof XWPFTable table) {
                    addTableNode(nodes, counter, table);
                }
            }
        }
        return nodes.isEmpty() ? placeholder() : nodes;
    }

    private void addParagraphNode(List<StructureNode> nodes, AtomicInteger counter, XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        Integer headingLevel = headingLevelOf(paragraph);
        String id = "n" + counter.getAndIncrement();
        if (headingLevel != null) {
            nodes.add(StructureNode.builder().id(id).type("heading").level(headingLevel).label(text.strip()).build());
        } else {
            nodes.add(StructureNode.builder().id(id).type("paragraph").label(truncate(text.strip())).build());
        }
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
        Matcher m = HEADING_STYLE.matcher(styleName);
        if (m.find()) {
            return Math.min(Integer.parseInt(m.group(1)), 3);
        }
        return null;
    }

    private void addTableNode(List<StructureNode> nodes, AtomicInteger counter, XWPFTable table) {
        List<String> columns = new ArrayList<>();
        if (!table.getRows().isEmpty()) {
            for (XWPFTableCell cell : table.getRows().get(0).getTableCells()) {
                String cellText = cell.getText() == null ? "" : cell.getText().strip();
                columns.add(cellText.isEmpty() ? "Colonne " + (columns.size() + 1) : cellText);
                if (columns.size() >= MAX_TABLE_COLUMNS) break;
            }
        }
        String id = "n" + counter.getAndIncrement();
        nodes.add(StructureNode.builder().id(id).type("table").label("Tableau").columns(columns).build());
    }

    private List<StructureNode> extractPdf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            String text = new PDFTextStripper().getText(document);
            return extractPlainText(text);
        }
    }

    private List<StructureNode> extractMarkdown(String text) {
        List<StructureNode> nodes = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(1);
        StringBuilder paragraph = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            Matcher m = MD_HEADING.matcher(line.strip());
            if (m.matches()) {
                flushParagraph(nodes, counter, paragraph);
                int level = Math.min(m.group(1).length(), 3);
                nodes.add(StructureNode.builder().id("n" + counter.getAndIncrement()).type("heading").level(level).label(m.group(2).strip()).build());
            } else if (line.isBlank()) {
                flushParagraph(nodes, counter, paragraph);
            } else {
                if (!paragraph.isEmpty()) paragraph.append(' ');
                paragraph.append(line.strip());
            }
        }
        flushParagraph(nodes, counter, paragraph);
        return nodes.isEmpty() ? placeholder() : nodes;
    }

    private void flushParagraph(List<StructureNode> nodes, AtomicInteger counter, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            nodes.add(StructureNode.builder().id("n" + counter.getAndIncrement()).type("paragraph").label(truncate(paragraph.toString())).build());
            paragraph.setLength(0);
        }
    }

    private List<StructureNode> extractPlainText(String text) {
        List<StructureNode> nodes = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(1);
        if (text != null) {
            for (String block : text.split("\\n\\s*\\n")) {
                String trimmed = block.strip().replaceAll("\\s+", " ");
                if (!trimmed.isEmpty()) {
                    nodes.add(StructureNode.builder().id("n" + counter.getAndIncrement()).type("paragraph").label(truncate(trimmed)).build());
                }
            }
        }
        return nodes.isEmpty() ? placeholder() : nodes;
    }

    private List<StructureNode> placeholder() {
        List<StructureNode> nodes = new ArrayList<>();
        nodes.add(StructureNode.builder().id("n1").type("paragraph").label("(Aucun contenu détecté dans ce document.)").build());
        return nodes;
    }

    private String truncate(String text) {
        return text.length() > PARAGRAPH_LABEL_MAX_LENGTH ? text.substring(0, PARAGRAPH_LABEL_MAX_LENGTH) + "…" : text;
    }
}
