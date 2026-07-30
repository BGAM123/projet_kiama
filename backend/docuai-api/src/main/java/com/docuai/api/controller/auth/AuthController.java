package com.docuai.api.controller.auth;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.ChangePasswordRequest;
import com.docuai.api.dto.auth.JwtResponse;
import com.docuai.api.dto.auth.LoginRequest;
import com.docuai.api.dto.auth.RefreshTokenRequest;
import com.docuai.api.service.AuthService;
import com.docuai.api.service.UserService;
import com.docuai.security.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Connexion, rafraîchissement, déconnexion, mot de passe")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authentifier un utilisateur et récupérer les jetons JWT")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request.getEmail(), request.getPassword())));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchir le jeton d'accès à partir d'un refresh token valide")
    public ResponseEntity<ApiResponse<JwtResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request.getRefreshToken())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion — révoque le refresh token (liste noire Redis)")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }

    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Changer son propre mot de passe (vérifie l'ancien mot de passe)")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        userService.changeOwnPassword(principal.getUtilisateur().getId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }
}
