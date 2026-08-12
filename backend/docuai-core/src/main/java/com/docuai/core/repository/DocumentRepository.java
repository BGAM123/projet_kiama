package com.docuai.core.repository;

import com.docuai.core.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByUtilisateur_IdOrderByDateCreationDesc(UUID userId);

    /** Fetch-join utilisé par le dashboard (Bloc 8) pour éviter le lazy-loading N+1 lors de l'agrégation par catégorie. */
    @Query("select d from Document d left join fetch d.documentType dt left join fetch dt.categorie where d.utilisateur.id = :userId")
    List<Document> findByUtilisateurIdWithDocumentTypeAndCategorie(@Param("userId") UUID userId);
}
