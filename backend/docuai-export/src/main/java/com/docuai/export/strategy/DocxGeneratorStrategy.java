package com.docuai.export.strategy;

import com.docuai.export.dto.ExportRequest;
import com.docuai.export.enums.ExportFormat;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class DocxGeneratorStrategy implements DocumentGeneratorStrategy {

    @Override
    public ExportFormat getSupportedFormat() {
        return ExportFormat.DOCX;
    }

    @Override
    public byte[] generateDocument(ExportRequest request) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Titre
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(request.getTitle() != null ? request.getTitle() : "Rapport DocuAI");
            titleRun.setBold(true);
            titleRun.setFontSize(16);

            // Contenu
            String text = request.getContent() != null ? request.getContent() : "";
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(line);
                run.setFontSize(11);
            }

            document.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du DOCX", e);
        }
    }

    @Override
    public String getFileExtension() {
        return ".docx";
    }

    @Override
    public String getContentType() {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }
}
