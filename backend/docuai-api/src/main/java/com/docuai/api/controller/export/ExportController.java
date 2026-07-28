package com.docuai.api.controller.export;

import com.docuai.export.dto.ExportRequest;
import com.docuai.export.service.ExportFacade;
import com.docuai.export.strategy.DocumentGeneratorStrategy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private final ExportFacade exportFacade;

    public ExportController(ExportFacade exportFacade) {
        this.exportFacade = exportFacade;
    }

    @PostMapping
    public ResponseEntity<byte[]> exportDocument(@RequestBody ExportRequest request) {
        // Validation basique
        if (request.getFormat() == null) {
            return ResponseEntity.badRequest().build();
        }

        DocumentGeneratorStrategy strategy = exportFacade.getStrategy(request.getFormat());
        byte[] documentBytes = strategy.generateDocument(request);

        String safeTitle = request.getTitle() != null 
            ? request.getTitle().replaceAll("[^a-zA-Z0-9.\\-]", "_") 
            : "export";
        String filename = safeTitle + strategy.getFileExtension();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(strategy.getContentType()))
                .contentLength(documentBytes.length)
                .body(documentBytes);
    }
}
