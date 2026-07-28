package com.docuai.export.strategy;

import com.docuai.export.dto.ExportRequest;
import com.docuai.export.enums.ExportFormat;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class PdfGeneratorStrategy implements DocumentGeneratorStrategy {

    @Override
    public ExportFormat getSupportedFormat() {
        return ExportFormat.PDF;
    }

    @Override
    public byte[] generateDocument(ExportRequest request) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                
                // Pour une appli de prod, il faudrait gérer le Custom Font et le word wrap
                // Ici, une version basique avec Helvetica
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
                contentStream.newLineAtOffset(50, 700);
                
                String safeTitle = request.getTitle() != null ? request.getTitle().replace("\n", " ").replace("\r", "") : "Document Export";
                contentStream.showText(safeTitle);
                
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(0, -30);
                
                String[] lines = request.getContent() != null ? request.getContent().split("\\r?\\n") : new String[0];
                for (String line : lines) {
                    // Nettoyage ultra basique de ce que Helvetica ne supporte pas (ex: caractères spéciaux poussés)
                    String safeLine = line.replace("\n", " ").replace("\r", "");
                    // En production, il faut découper (wrap) la ligne si elle est trop longue pour la page
                    contentStream.showText(safeLine);
                    contentStream.newLineAtOffset(0, -15);
                }
                
                contentStream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du PDF", e);
        }
    }

    @Override
    public String getFileExtension() {
        return ".pdf";
    }

    @Override
    public String getContentType() {
        return "application/pdf";
    }
}
