package com.docuai.core.repository;

import com.docuai.core.model.DocumentSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentSectionRepository extends JpaRepository<DocumentSection, UUID> {

    List<DocumentSection> findByDocument_IdOrderByOrderIndexAsc(UUID documentId);

    List<DocumentSection> findByDocument_IdInOrderByOrderIndexAsc(List<UUID> documentIds);
}
