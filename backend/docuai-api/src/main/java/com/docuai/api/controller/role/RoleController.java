package com.docuai.api.controller.role;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.CreateRoleRequest;
import com.docuai.api.dto.RoleDTO;
import com.docuai.api.dto.UpdateRoleRequest;
import com.docuai.api.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Role Management", description = "CRUD rôles et affectation de permissions")
@PreAuthorize("hasAuthority('ROLES_MANAGE')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @Operation(summary = "Lister tous les rôles")
    public ResponseEntity<ApiResponse<List<RoleDTO>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.success(roleService.listAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un rôle")
    public ResponseEntity<ApiResponse<RoleDTO>> getRoleById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Créer un rôle")
    public ResponseEntity<ApiResponse<RoleDTO>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roleService.create(request)));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @Operation(summary = "Modifier un rôle")
    public ResponseEntity<ApiResponse<RoleDTO>> updateRole(@PathVariable UUID id, @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un rôle (refusé s'il est assigné à un utilisateur)")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteRole(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id.toString())));
    }
}
