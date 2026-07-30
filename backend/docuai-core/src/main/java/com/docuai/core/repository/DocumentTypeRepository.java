package com.docuai.core.repository;

import com.docuai.core.model.DocumentType;
import com.docuai.core.model.DocumentTypeStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, UUID> {

    boolean existsByCategorie_Id(UUID categorieId);

    List<DocumentType> findByCategorie_Id(UUID categorieId);

    List<DocumentType> findByStatut(DocumentTypeStatut statut);

    List<DocumentType> findByCategorie_IdAndStatut(UUID categorieId, DocumentTypeStatut statut);
}
