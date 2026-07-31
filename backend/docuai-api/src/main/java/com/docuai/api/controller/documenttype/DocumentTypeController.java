package com.docuai.api.controller.documenttype;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.DocumentStructureDTO;
import com.docuai.api.dto.DocumentTypeDTO;
import com.docuai.api.dto.UpdateDocumentTypeRequest;
import com.docuai.api.dto.UpdateStructureRequest;
import com.docuai.api.service.DocumentTypeExtractionService;
import com.docuai.api.service.DocumentTypeService;
import com.docuai.core.model.DocumentTypeStatut;
import com.docuai.security.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD référentiel des Documents Types (nom/description/catégorie) + lecture
 * et correction de leur structure (Bloc 3), ainsi que l'import d'un fichier
 * source, la (ré)extraction de sa structure et la validation/activation
 * (Bloc 4, cf. DocumentTypeExtractionService / DocumentTypeService#validate).
 */
@RestController
@RequestMapping("/api/v1/document-types")
@Tag(name = "Document Types", description = "Référentiel des Documents Types et de leur structure")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;
    private final DocumentTypeExtractionService documentTypeExtractionService;

    public DocumentTypeController(DocumentTypeService documentTypeService,
                                   DocumentTypeExtractionService documentTypeExtractionService) {
        this.documentTypeService = documentTypeService;
        this.documentTypeExtractionService = documentTypeExtractionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENT_TYPE_READ')")
    @Operation(summary = "Lister les Documents Types (filtres optionnels categoryId/status)")
    public ResponseEntity<ApiResponse<List<DocumentTypeDTO>>> getAll(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) DocumentTypeStatut status) {
        return ResponseEntity.ok(ApiResponse.success(documentTypeService.listAll(categoryId, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_TYPE_READ')")
    @Operation(summary = "Détail d'un Document Type")
    public ResponseEntity<ApiResponse<DocumentTypeDTO>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(documentTypeService.getById(id)));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('DOCUMENT_TYPE_MANAGE')")
    @Operation(summary = "Modifier un Document Type (nom/description/catégorie)")
    public ResponseEntity<ApiResponse<DocumentTypeDTO>> update(@PathVariable UUID id, @RequestBody UpdateDocumentTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(documentTypeService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_TYPE_MANAGE')")
    @Operation(summary = "Archiver un Document Type (suppression logique, statut -> ARCHIVE)")
    public ResponseEntity<ApiResponse<Map<String, String>>> archive(@PathVariable UUID id) {
        documentTypeService.archive(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id.toString())));
    }

    @GetMapping("/{id}/structure")
    @PreAuthorize("hasAuthority('DOCUMENT_TYPE_READ')")
    @Operation(summary = "Consulter la structure extraite d'un Document Type")
    public ResponseEntity<ApiResponse<DocumentStructureDTO>> getStructure(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(documentTypeService.getStructure(id)));
    }

    @PutMapping("/{id}/structure")
    @PreAuthorize("hasAuthority('DOCUMENT_TYPE_MANAGE')")
    @Operation(summary = "Corriger manuellement la structure d'un Document Type")
    public ResponseEntity<ApiResponse<DocumentStructureDTO>> updateStructure(
            @PathVariable UUID id, @Valid @RequestBody UpdateStructureRequest request) {
        return ResponseEntity.ok(ApiResponse.success(documentTypeService.updateStructure(id, request)));
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('DOCUMENT_TYPE_IMPORT')")
    @Operation(summary = "Importer un fichier source : crée le Document Type et extrait sa structure")
    public ResponseEntity<ApiResponse<DocumentTypeDTO>> importDocumentType(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("categoryId") UUID categoryId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                documentTypeExtractionService.importAndExtract(file, name, description, categoryId, principal.getUtilisateur())));
    }

    @PostMapping("/{id}/extract")
    @PreAuthorize("hasAuthority('DOCUMENT_TYPE_EXTRACT')")
    @Operation(summary = "Relancer l'extraction de structure à partir du fichier source déjà stocké")
    public ResponseEntity<ApiResponse<DocumentTypeDTO>> reextract(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(documentTypeExtractionService.reextract(id)));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('DOCUMENT_TYPE_MANAGE')")
    @Operation(summary = "Valider et activer un Document Type dont la structure a été extraite")
    public ResponseEntity<ApiResponse<DocumentTypeDTO>> validate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(documentTypeService.validate(id)));
    }
}
