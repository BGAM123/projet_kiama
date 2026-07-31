package com.docuai.core.repository;

import com.docuai.core.model.DocumentGenere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentGenereRepository extends JpaRepository<DocumentGenere, UUID> {

    List<DocumentGenere> findByUtilisateur_IdOrderByDateCreationDesc(UUID userId);

    List<DocumentGenere> findByConversation_IdOrderByDateCreationDesc(UUID conversationId);
}
