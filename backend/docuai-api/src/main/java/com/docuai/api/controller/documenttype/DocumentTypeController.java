package com.docuai.api.controller.documenttype;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.DocumentStructureDTO;
import com.docuai.api.dto.DocumentTypeDTO;
import com.docuai.api.dto.UpdateDocumentTypeRequest;
import com.docuai.api.dto.UpdateStructureRequest;
import com.docuai.api.service.DocumentTypeService;
import com.docuai.core.model.DocumentTypeStatut;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD référentiel des Documents Types (nom/description/catégorie) + lecture
 * et correction de leur structure. L'import de fichier et le déclenchement
 * d'extraction (POST .../import, .../extract) sont livrés au Bloc 4 — hors
 * périmètre de ce contrôleur.
 */
@RestController
@RequestMapping("/api/v1/document-types")
@Tag(name = "Document Types", description = "Référentiel des Documents Types et de leur structure")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    public DocumentTypeController(DocumentTypeService documentTypeService) {
        this.documentTypeService = documentTypeService;
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
}
