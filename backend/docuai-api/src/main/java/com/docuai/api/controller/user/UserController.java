package com.docuai.api.controller.user;

import com.docuai.api.dto.ApiResponse;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.UtilisateurRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "Endpoints de gestion des utilisateurs")
public class UserController {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @Operation(summary = "Lister tous les utilisateurs")
    public ResponseEntity<ApiResponse<List<Utilisateur>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(utilisateurRepository.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public ResponseEntity<ApiResponse<Utilisateur>> getUserById(@PathVariable UUID id) {
        return utilisateurRepository.findById(id)
                .map(user -> ResponseEntity.ok(ApiResponse.success(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Le POST, PUT et DELETE (désactivation) peuvent être ajoutés ici avec leurs DTOs pour un CRUD complet.
}
