package com.docuai.api.service;

import com.docuai.api.dto.CreateRoleRequest;
import com.docuai.api.dto.RoleDTO;
import com.docuai.api.dto.UpdateRoleRequest;
import com.docuai.api.exception.BusinessException;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.RoleMapper;
import com.docuai.core.model.Permission;
import com.docuai.core.model.Role;
import com.docuai.core.repository.PermissionRepository;
import com.docuai.core.repository.RoleRepository;
import com.docuai.core.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Service applicatif : CRUD rôles + affectation de permissions granulaires. */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final RoleMapper roleMapper;

    public RoleService(RoleRepository roleRepository,
                        PermissionRepository permissionRepository,
                        UtilisateurRepository utilisateurRepository,
                        RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.roleMapper = roleMapper;
    }

    @Transactional(readOnly = true)
    public List<RoleDTO> listAll() {
        return roleRepository.findAll().stream().map(roleMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoleDTO getById(UUID id) {
        return roleMapper.toDto(findEntity(id));
    }

    @Transactional
    public RoleDTO create(CreateRoleRequest request) {
        if (roleRepository.existsByNom(request.getName())) {
            throw new IllegalArgumentException("Un rôle avec ce nom existe déjà.");
        }
        Role role = Role.builder()
                .nom(request.getName())
                .description(request.getDescription())
                .permissions(resolvePermissions(request.getPermissionIds()))
                .build();
        return roleMapper.toDto(roleRepository.save(role));
    }

    @Transactional
    public RoleDTO update(UUID id, UpdateRoleRequest request) {
        Role role = findEntity(id);
        if (request.getName() != null) role.setNom(request.getName());
        if (request.getDescription() != null) role.setDescription(request.getDescription());
        if (request.getPermissionIds() != null) role.setPermissions(resolvePermissions(request.getPermissionIds()));
        return roleMapper.toDto(roleRepository.save(role));
    }

    /** Un rôle assigné à au moins un utilisateur ne peut pas être supprimé (règle de gestion, cf. §6). */
    @Transactional
    public void delete(UUID id) {
        Role role = findEntity(id);
        if (utilisateurRepository.existsByRolesContaining(role)) {
            throw BusinessException.conflict("ROLE_IN_USE", "Ce rôle est assigné à au moins un utilisateur et ne peut pas être supprimé.");
        }
        roleRepository.deleteById(id);
    }

    private Set<Permission> resolvePermissions(List<UUID> permissionIds) {
        if (permissionIds == null) return new HashSet<>();
        List<Permission> found = permissionRepository.findAllById(permissionIds);
        if (found.size() != new HashSet<>(permissionIds).size()) {
            Set<UUID> foundIds = found.stream().map(Permission::getId).collect(Collectors.toSet());
            UUID missing = permissionIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
            throw new NotFoundException("PERMISSION_NOT_FOUND", "Permission introuvable : " + missing);
        }
        return new HashSet<>(found);
    }

    private Role findEntity(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND", "Rôle introuvable."));
    }
}
