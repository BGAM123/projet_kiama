package com.docuai.api.controller.document;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.CreateDocumentRequest;
import com.docuai.api.dto.DocumentDTO;
import com.docuai.api.dto.DocumentSectionDTO;
import com.docuai.api.dto.SectionSuggestionDTO;
import com.docuai.api.dto.UpdateSectionContentRequest;
import com.docuai.api.service.DocumentSectionService;
import com.docuai.api.service.DocumentService;
import com.docuai.export.ExportFormat;
import com.docuai.export.ExportedFile;
import com.docuai.security.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Édition manuelle assistée par section (refonte remplaçant le flux
 * conversationnel de génération) : création d'un document à partir d'un
 * Document Type, sauvegarde du contenu rédigé par l'utilisateur,
 * amélioration/acceptation/rejet de suggestions IA par section, finalisation
 * et export. Base {@code /api/v1/documents} partagée avec {@link
 * com.docuai.api.controller.document.DocumentUploadController} (utilitaire
 * d'extraction brute pour le Bloc 4, {@code /documents/upload}) — routes
 * disjointes, pas de collision.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Édition manuelle de documents assistée par IA, section par section")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentSectionService documentSectionService;

    public DocumentController(DocumentService documentService, DocumentSectionService documentSectionService) {
        this.documentService = documentService;
        this.documentSectionService = documentSectionService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DOCUMENT_GENERATE')")
    @Operation(summary = "Créer un document à partir d'un Document Type (sections vides initialisées depuis son squelette)")
    public ResponseEntity<ApiResponse<DocumentDTO>> create(@Valid @RequestBody CreateDocumentRequest request,
                                                             @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(documentService.create(request, principal.getUtilisateur())));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('HISTORY_READ_OWN', 'HISTORY_READ_ALL')")
    @Operation(summary = "Lister les documents d'un utilisateur")
    public ResponseEntity<ApiResponse<List<DocumentDTO>>> listForUser(
            @RequestParam UUID userId, @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.listForUser(userId, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('HISTORY_READ_OWN', 'HISTORY_READ_ALL')")
    @Operation(summary = "Détail d'un document avec l'arbre de ses sections")
    public ResponseEntity<ApiResponse<DocumentDTO>> getById(@PathVariable UUID id,
                                                              @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.getById(id, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @PutMapping("/{id}/sections/{sectionId}")
    @PreAuthorize("hasAuthority('DOCUMENT_EDIT_OWN')")
    @Operation(summary = "Sauvegarder le contenu rédigé par l'utilisateur pour une section (autosave)")
    public ResponseEntity<ApiResponse<DocumentSectionDTO>> saveSectionContent(
            @PathVariable UUID id, @PathVariable UUID sectionId,
            @RequestBody UpdateSectionContentRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(documentSectionService.saveContent(
                id, sectionId, request.getContent(), principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @PostMapping("/{id}/sections/{sectionId}/improve")
    @PreAuthorize("hasAuthority('DOCUMENT_EDIT_OWN')")
    @Operation(summary = "Demander une amélioration IA du contenu déjà écrit (suggestion retournée sans être appliquée)")
    public ResponseEntity<ApiResponse<SectionSuggestionDTO>> improveSection(
            @PathVariable UUID id, @PathVariable UUID sectionId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(documentSectionService.improve(
                id, sectionId, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @PostMapping("/{id}/sections/{sectionId}/apply-suggestion")
    @PreAuthorize("hasAuthority('DOCUMENT_EDIT_OWN')")
    @Operation(summary = "Appliquer explicitement la dernière suggestion IA comme nouveau contenu de la section")
    public ResponseEntity<ApiResponse<DocumentSectionDTO>> applySuggestion(
            @PathVariable UUID id, @PathVariable UUID sectionId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(documentSectionService.applySuggestion(
                id, sectionId, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @PostMapping("/{id}/sections/{sectionId}/reject-suggestion")
    @PreAuthorize("hasAuthority('DOCUMENT_EDIT_OWN')")
    @Operation(summary = "Rejeter la dernière suggestion IA (effacée sans affecter le contenu retenu)")
    public ResponseEntity<ApiResponse<DocumentSectionDTO>> rejectSuggestion(
            @PathVariable UUID id, @PathVariable UUID sectionId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(documentSectionService.rejectSuggestion(
                id, sectionId, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAuthority('DOCUMENT_EXPORT')")
    @Operation(summary = "Assembler les sections, exporter en DOCX et stocker sur MinIO")
    public ResponseEntity<ApiResponse<DocumentDTO>> finalizeDocument(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(documentService.finalizeDocument(
                id, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    /**
     * Téléchargement direct dans le format demandé (DOCX/PDF/Markdown), utilisé
     * juste après la finalisation. Réponse binaire brute — pas d'enveloppe
     * {@code ApiResponse} (cf. {@code ApiResponseWrapperAdvice}, qui exclut
     * explicitement {@code byte[]}).
     */
    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('DOCUMENT_EXPORT')")
    @Operation(summary = "Exporter le document dans le format demandé (DOCX, PDF ou MARKDOWN)")
    public ResponseEntity<byte[]> export(@PathVariable UUID id,
                                          @RequestParam(defaultValue = "DOCX") ExportFormat format,
                                          @AuthenticationPrincipal UserDetailsImpl principal) {
        ExportedFile file = documentService.export(id, format, principal.getUtilisateur().getId(), isAdmin(principal));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }

    private boolean isAdmin(UserDetailsImpl principal) {
        return principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
