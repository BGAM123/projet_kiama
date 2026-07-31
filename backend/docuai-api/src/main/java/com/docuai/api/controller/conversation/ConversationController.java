package com.docuai.api.controller.conversation;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.ConversationDTO;
import com.docuai.api.dto.CreateConversationRequest;
import com.docuai.api.dto.MessageDTO;
import com.docuai.api.dto.ReferenceDocumentDTO;
import com.docuai.api.dto.SendMessageRequest;
import com.docuai.api.service.ConversationService;
import com.docuai.security.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Conversations IA (chat) et documents de référence (Bloc 6, permission
 * {@code CONVERSATION_USE}). Un utilisateur ne peut agir que sur ses propres
 * conversations ; un ADMIN (toutes permissions) peut consulter celles de
 * n'importe qui — ownership vérifiée dans {@link ConversationService}, même
 * pattern que les notifications.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations", description = "Chat IA et documents de référence")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONVERSATION_USE')")
    @Operation(summary = "Démarrer une conversation")
    public ResponseEntity<ApiResponse<ConversationDTO>> create(@Valid @RequestBody CreateConversationRequest request,
                                                                @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.create(request, principal.getUtilisateur().getId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONVERSATION_USE') and (#userId == principal.utilisateur.id or hasRole('ADMIN'))")
    @Operation(summary = "Lister les conversations d'un utilisateur")
    public ResponseEntity<ApiResponse<List<ConversationDTO>>> listForUser(@RequestParam @P("userId") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(conversationService.listForUser(userId)));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('CONVERSATION_USE')")
    @Operation(summary = "Lister les messages d'une conversation")
    public ResponseEntity<ApiResponse<List<MessageDTO>>> listMessages(@PathVariable UUID id,
                                                                       @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.listMessages(id, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('CONVERSATION_USE')")
    @Operation(summary = "Envoyer un message et recevoir la réponse de l'assistant IA")
    public ResponseEntity<ApiResponse<MessageDTO>> sendMessage(@PathVariable UUID id,
                                                                @Valid @RequestBody SendMessageRequest request,
                                                                @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.sendMessage(id, request.getContent(), principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @GetMapping("/{id}/reference-documents")
    @PreAuthorize("hasAuthority('CONVERSATION_USE')")
    @Operation(summary = "Lister les documents de référence d'une conversation")
    public ResponseEntity<ApiResponse<List<ReferenceDocumentDTO>>> listReferenceDocuments(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.listReferenceDocuments(id, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    @PostMapping(value = "/{id}/reference-documents", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('CONVERSATION_USE')")
    @Operation(summary = "Importer un document de référence (indexé pour le RAG)")
    public ResponseEntity<ApiResponse<ReferenceDocumentDTO>> addReferenceDocument(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.addReferenceDocument(id, file, principal.getUtilisateur().getId(), isAdmin(principal))));
    }

    private boolean isAdmin(UserDetailsImpl principal) {
        return principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
