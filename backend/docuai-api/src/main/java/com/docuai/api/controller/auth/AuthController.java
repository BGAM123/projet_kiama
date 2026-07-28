package com.docuai.api.controller.auth;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.auth.JwtResponse;
import com.docuai.api.dto.auth.LoginRequest;
import com.docuai.api.dto.auth.RefreshTokenRequest;
import com.docuai.security.jwt.JwtTokenProvider;
import com.docuai.security.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints de connexion et rafraîchissement")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    @Operation(summary = "Authentifier un utilisateur et récupérer les JWT")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody LoginRequest request) {
        
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        List<String> roles = authorities.stream().filter(a -> a.startsWith("ROLE_")).collect(Collectors.toList());
        List<String> permissions = authorities.stream().filter(a -> !a.startsWith("ROLE_")).collect(Collectors.toList());

        JwtResponse jwtResponse = new JwtResponse(jwt, refreshToken, userDetails.getUsername(), roles, permissions);

        return ResponseEntity.ok(ApiResponse.success(jwtResponse));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchir le jeton d'accès")
    public ResponseEntity<ApiResponse<JwtResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();
        
        if (tokenProvider.validateToken(requestRefreshToken)) {
            String username = tokenProvider.getUsernameFromJWT(requestRefreshToken);
            
            // Recharger l'authentification (pour simplifier, on suppose que l'auth object n'est plus en contexte pour ce test)
            // Dans un cas réel on doit valider que l'utilisateur existe toujours etc.
            // ...
            // Nous construisons la logique de rafraîchissement au complet si besoin ultérieurement
        }
        
        return ResponseEntity.badRequest().body(ApiResponse.error("Jeton de rafraîchissement invalide ou expiré"));
    }
}
