package com.docuai.api.controller.generation;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.GeneratedDocumentDTO;
import com.docuai.api.dto.StartGenerationRequest;
import com.docuai.api.dto.UpdateGenerationRequest;
import com.docuai.api.service.GenerationService;
import com.docuai.api.service.GenerationStreamService;
import com.docuai.security.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Génération documentaire (Bloc 6) : démarrage, consultation/édition,
 * historique, et suivi en direct via SSE ({@code GET /{id}/stream}, exclu de
 * l'enveloppe {@code ApiResponse} — voir {@code ApiResponseWrapperAdvice}).
 */
@RestController
@RequestMapping("/api/v1/generations")
@Tag(name = "Générations", description = "Génération documentaire par IA (section par section, suivi SSE)")
public class GenerationController {

    private final GenerationService generationService;
    private final GenerationStreamService generationStreamService;

    public GenerationController(GenerationService generationService, GenerationStreamService generationStreamService) {
        this.generationService = generationService;
        this.generationStreamService = generationStreamService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DOCUMENT_GENERATE')")
    @Operation(summary = "Démarrer une génération (crée le document, sections initialisées)")
    public ResponseEntity<ApiResponse<GeneratedDocumentDTO>> start(@Valid @RequestBody StartGenerationRequest request,
                                                                     @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                generationService.start(request, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('HISTORY_READ_OWN', 'HISTORY_READ_ALL')")
    @Operation(summary = "Lister les documents générés d'un utilisateur")
    public ResponseEntity<ApiResponse<List<GeneratedDocumentDTO>>> listForUser(
            @RequestParam UUID userId, @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                generationService.listForUser(userId, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('HISTORY_READ_OWN', 'HISTORY_READ_ALL')")
    @Operation(summary = "Détail d'un document généré")
    public ResponseEntity<ApiResponse<GeneratedDocumentDTO>> getById(@PathVariable UUID id,
                                                                      @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                generationService.getById(id, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_EDIT_OWN')")
    @Operation(summary = "Éditer manuellement le contenu d'un document généré")
    public ResponseEntity<ApiResponse<GeneratedDocumentDTO>> update(@PathVariable UUID id,
                                                                      @RequestBody UpdateGenerationRequest request,
                                                                      @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                generationService.update(id, request, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @GetMapping("/{id}/stream")
    @PreAuthorize("hasAuthority('DOCUMENT_GENERATE')")
    @Operation(summary = "Suivre la génération en direct (SSE : events progress/section/done)")
    public SseEmitter stream(@PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl principal) {
        SseEmitter emitter = generationStreamService.createEmitter();
        generationStreamService.run(id, emitter, principal.getUtilisateur().getId(), isAdmin(principal));
        return emitter;
    }

    private boolean isAdmin(UserDetailsImpl principal) {
        return principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
