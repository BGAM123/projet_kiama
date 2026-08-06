package com.docuai.core.repository;

import com.docuai.core.model.Message;
import com.docuai.core.model.MessageRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversation_IdOrderByDateCreationAsc(UUID conversationId);

    /** Historique paginé exposé par l'API (exclut SYSTEM au niveau requête — filtre déjà appliqué en mémoire ailleurs, mais nécessaire ici pour un compte de page correct). */
    Page<Message> findByConversation_IdAndRoleNotOrderByDateCreationAsc(UUID conversationId, MessageRole excludedRole, Pageable pageable);
}
