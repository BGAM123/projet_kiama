package com.docuai.api.controller.export;

import com.docuai.api.dto.ExportRequest;
import com.docuai.export.ExportContent;
import com.docuai.export.ExportService;
import com.docuai.export.ExportedFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Export d'un document généré vers DOCX/PDF/Markdown (Bloc 7). Réponse
 * binaire brute (pas d'enveloppe {@code ApiResponse}, cf.
 * {@code ApiResponseWrapperAdvice}).
 */
@RestController
@RequestMapping("/api/v1/export")
@Tag(name = "Export", description = "Export de documents générés (DOCX/PDF/Markdown)")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DOCUMENT_EXPORT')")
    @Operation(summary = "Exporter un document (titre + contenu) vers le format demandé")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ExportRequest request) {
        ExportContent content = new ExportContent(request.getTitle(), request.getContent(), request.getHeaderText(), request.getFooterText());
        ExportedFile file = exportService.export(content, request.getFormat());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }
}
