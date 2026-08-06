package com.docuai.export;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class MarkdownDocumentExporter implements DocumentExporter {

    @Override
    public ExportFormat supportedFormat() {
        return ExportFormat.MARKDOWN;
    }

    @Override
    public ExportedFile export(ExportContent request) {
        String heading = (request.title() == null || request.title().isBlank()) ? "Document" : request.title();
        StringBuilder markdown = new StringBuilder();
        if (request.headerText() != null && !request.headerText().isBlank()) {
            markdown.append("<!-- en-tête : ").append(request.headerText()).append(" -->\n\n");
        }
        markdown.append("# ").append(heading).append("\n\n").append(request.content() == null ? "" : request.content());
        if (request.footerText() != null && !request.footerText().isBlank()) {
            markdown.append("\n\n<!-- pied de page : ").append(request.footerText()).append(" -->");
        }
        return new ExportedFile(
                markdown.toString().getBytes(StandardCharsets.UTF_8),
                ExportFilenames.build(request.title(), "md"),
                "text/markdown; charset=UTF-8");
    }
}
