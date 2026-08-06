package com.docuai.api.controller.dashboard;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.DashboardStatsDTO;
import com.docuai.api.service.DashboardService;
import com.docuai.security.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Statistiques de l'écran d'accueil (Bloc 8). */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Statistiques agrégées de l'écran d'accueil")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('DASHBOARD_READ_OWN', 'DASHBOARD_READ_ALL')")
    @Operation(summary = "Statistiques du tableau de bord d'un utilisateur")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getStats(@RequestParam UUID userId,
                                                                     @AuthenticationPrincipal UserDetailsImpl principal) {
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getStats(userId, principal.getUtilisateur().getId(), isAdmin)));
    }
}
