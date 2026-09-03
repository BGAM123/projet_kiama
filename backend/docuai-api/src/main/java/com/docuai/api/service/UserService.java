package com.docuai.api.service;

import com.docuai.api.dto.CreateUserRequest;
import com.docuai.api.dto.UpdateUserRequest;
import com.docuai.api.dto.UserDTO;
import com.docuai.api.event.UserCreatedEvent;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.UserMapper;
import com.docuai.core.model.Role;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.RoleRepository;
import com.docuai.core.repository.UtilisateurRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service applicatif : CRUD utilisateurs + gestion de mot de passe
 * (section 6 : "Gestion des utilisateurs : CRUD utilisateurs, activation/
 * désactivation, affectation de rôles").
 */
@Service
public class UserService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UtilisateurRepository utilisateurRepository,
                        RoleRepository roleRepository,
                        UserMapper userMapper,
                        PasswordEncoder passwordEncoder,
                        ApplicationEventPublisher eventPublisher) {
        this.utilisateurRepository = utilisateurRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<UserDTO> listAll() {
        return utilisateurRepository.findAll().stream().map(userMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDTO getById(UUID id) {
        return userMapper.toDto(findEntity(id));
    }

    @Transactional
    public UserDTO create(CreateUserRequest request) {
        if (utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Un utilisateur avec cet email existe déjà.");
        }
        String rawPassword = (request.getPassword() != null && !request.getPassword().isBlank())
                ? request.getPassword() : generateRandomPassword();

        Utilisateur user = Utilisateur.builder()
                .email(request.getEmail())
                .prenom(request.getFirstName())
                .nom(request.getLastName())
                .actif(request.getActive() == null || request.getActive())
                .motDePasseHash(passwordEncoder.encode(rawPassword))
                .roles(resolveRoles(request.getRoleIds()))
                .build();
        Utilisateur saved = utilisateurRepository.save(user);

        // rawPassword ne quitte jamais cette méthode autrement que via cet
        // événement (jamais stocké, jamais renvoyé dans le DTO) — écouté de
        // façon asynchrone et après commit par UserWelcomeEmailListener pour
        // envoyer l'e-mail de bienvenue sans bloquer cette requête HTTP.
        eventPublisher.publishEvent(new UserCreatedEvent(
                saved.getId(), saved.getEmail(), saved.getPrenom(), saved.getNom(), rawPassword));

        return userMapper.toDto(saved);
    }

    @Transactional
    public UserDTO update(UUID id, UpdateUserRequest request) {
        Utilisateur user = findEntity(id);
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getFirstName() != null) user.setPrenom(request.getFirstName());
        if (request.getLastName() != null) user.setNom(request.getLastName());
        if (request.getActive() != null) user.setActif(request.getActive());
        if (request.getRoleIds() != null) user.setRoles(resolveRoles(request.getRoleIds()));
        return userMapper.toDto(utilisateurRepository.save(user));
    }

    /** Désactivation logique (actif=false) plutôt que suppression physique — préserve l'intégrité référentielle. */
    @Transactional
    public void deactivate(UUID id) {
        Utilisateur user = findEntity(id);
        user.setActif(false);
        utilisateurRepository.save(user);
    }

    @Transactional
    public void changeOwnPassword(UUID userId, String currentPassword, String newPassword) {
        Utilisateur user = findEntity(userId);
        if (!passwordEncoder.matches(currentPassword, user.getMotDePasseHash())) {
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect.");
        }
        validatePasswordStrength(newPassword);
        user.setMotDePasseHash(passwordEncoder.encode(newPassword));
        utilisateurRepository.save(user);
    }

    @Transactional
    public void adminResetPassword(UUID userId, String newPassword) {
        Utilisateur user = findEntity(userId);
        validatePasswordStrength(newPassword);
        user.setMotDePasseHash(passwordEncoder.encode(newPassword));
        utilisateurRepository.save(user);
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins 8 caractères.");
        }
    }

    private Set<Role> resolveRoles(List<UUID> roleIds) {
        if (roleIds == null) return new HashSet<>();
        List<Role> found = roleRepository.findAllById(roleIds);
        if (found.size() != new HashSet<>(roleIds).size()) {
            Set<UUID> foundIds = found.stream().map(Role::getId).collect(Collectors.toSet());
            UUID missing = roleIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
            throw new NotFoundException("ROLE_NOT_FOUND", "Rôle introuvable : " + missing);
        }
        return new HashSet<>(found);
    }

    private Utilisateur findEntity(UUID id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Utilisateur introuvable."));
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder("Aa2!");
        for (int i = 0; i < 8; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
