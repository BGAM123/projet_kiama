package com.docuai.core.repository;

import com.docuai.core.model.Role;
import com.docuai.core.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {
    Optional<Utilisateur> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from Utilisateur u join u.roles r where r = :role")
    boolean existsByRolesContaining(Role role);
}
