package com.docuai.api.controller.user;

import com.docuai.api.dto.*;
import com.docuai.api.service.UserService;
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
 * CRUD utilisateurs (section 6/7 du prompt maître). Le code de permission
 * ('USERS_MANAGE') correspond à celui seedé par V2__seed_roles_permissions.sql.
 * Mapping des mises à jour sur PUT (littéral du prompt maître, section 7) ET
 * PATCH (contrat déjà observé côté un frontend consommateur type React Query)
 * — les deux vocabulaires HTTP sont acceptés pour ne pas figer un choix côté
 * client à ce stade.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "CRUD utilisateurs, activation/désactivation, affectation de rôles")
@PreAuthorize("hasAuthority('USERS_MANAGE')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Lister tous les utilisateurs")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.listAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un utilisateur")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Créer un utilisateur")
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.create(request)));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @Operation(summary = "Modifier un utilisateur")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Désactiver un utilisateur (suppression logique)")
    public ResponseEntity<ApiResponse<Map<String, String>>> deactivateUser(@PathVariable UUID id) {
        userService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id.toString())));
    }

    @RequestMapping(value = "/{id}/password", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @Operation(summary = "Réinitialiser le mot de passe d'un utilisateur (admin)")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> resetPassword(@PathVariable UUID id,
                                                                            @Valid @RequestBody ResetPasswordRequest request) {
        userService.adminResetPassword(id, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }
}
