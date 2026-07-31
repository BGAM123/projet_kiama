package com.docuai.api.controller.document;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.ExtractedContentDTO;
import com.docuai.api.service.DocumentUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Brique bas niveau du Bloc 4 (extraction) : upload d'un fichier source +
 * extraction de son texte brut (Tika) + stockage MinIO. Ne crée PAS de
 * Document Type — cf. DocumentTypeController (Bloc 3, CRUD référentiel
 * uniquement) et DocumentUploadService pour le détail du découpage.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Upload et extraction de texte brut d'un fichier source")
public class DocumentUploadController {

    private final DocumentUploadService documentUploadService;

    public DocumentUploadController(DocumentUploadService documentUploadService) {
        this.documentUploadService = documentUploadService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('DOCUMENT_TYPE_IMPORT')")
    @Operation(summary = "Uploader un fichier source et en extraire le texte brut")
    public ResponseEntity<ApiResponse<ExtractedContentDTO>> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(documentUploadService.upload(file)));
    }
}
