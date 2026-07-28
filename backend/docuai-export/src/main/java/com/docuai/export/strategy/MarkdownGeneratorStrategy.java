package com.docuai.export.strategy;

import com.docuai.export.dto.ExportRequest;
import com.docuai.export.enums.ExportFormat;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class MarkdownGeneratorStrategy implements DocumentGeneratorStrategy {

    @Override
    public ExportFormat getSupportedFormat() {
        return ExportFormat.MARKDOWN;
    }

    @Override
    public byte[] generateDocument(ExportRequest request) {
        String content = request.getContent() != null ? request.getContent() : "";
        if (request.getTitle() != null && !content.startsWith("#")) {
            content = "# " + request.getTitle() + "\n\n" + content;
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String getFileExtension() {
        return ".md";
    }

    @Override
    public String getContentType() {
        return "text/markdown";
    }
}
