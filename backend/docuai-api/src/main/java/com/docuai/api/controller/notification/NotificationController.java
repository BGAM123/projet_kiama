package com.docuai.api.controller.notification;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.NotificationDTO;
import com.docuai.api.service.NotificationService;
import com.docuai.security.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consultation et marquage "lue" des notifications (section 5/6, permission
 * {@code NOTIFICATION_READ_OWN}). Un utilisateur ne peut lire/marquer que ses
 * propres notifications ; un ADMIN (toutes permissions) peut consulter celles
 * de n'importe quel utilisateur.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Notifications applicatives de l'utilisateur")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ_OWN') and (#userId == principal.utilisateur.id or hasRole('ADMIN'))")
    @Operation(summary = "Lister les notifications d'un utilisateur")
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getForUser(@PathVariable @P("userId") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.listForUser(userId)));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ_OWN')")
    @Operation(summary = "Marquer une notification comme lue")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> markRead(@PathVariable UUID id,
                                                                        @AuthenticationPrincipal UserDetailsImpl principal) {
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        notificationService.markRead(id, principal.getUtilisateur().getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }
}
