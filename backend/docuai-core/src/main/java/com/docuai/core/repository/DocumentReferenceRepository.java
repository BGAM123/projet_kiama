package com.docuai.core.repository;

import com.docuai.core.model.DocumentReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentReferenceRepository extends JpaRepository<DocumentReference, UUID> {

    List<DocumentReference> findByConversation_IdOrderByDateImportAsc(UUID conversationId);

    long countByConversation_Id(UUID conversationId);
}
