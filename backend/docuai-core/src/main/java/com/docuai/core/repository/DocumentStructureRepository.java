package com.docuai.core.repository;

import com.docuai.core.model.DocumentStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentStructureRepository extends JpaRepository<DocumentStructure, UUID> {
    Optional<DocumentStructure> findByDocumentType_Id(UUID documentTypeId);
}
