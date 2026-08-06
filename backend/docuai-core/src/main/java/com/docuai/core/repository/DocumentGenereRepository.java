package com.docuai.core.repository;

import com.docuai.core.model.DocumentGenere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentGenereRepository extends JpaRepository<DocumentGenere, UUID> {

    List<DocumentGenere> findByUtilisateur_IdOrderByDateCreationDesc(UUID userId);

    List<DocumentGenere> findByConversation_IdOrderByDateCreationDesc(UUID conversationId);

    /** Fetch-join utilisé par le dashboard (Bloc 8) pour éviter le lazy-loading N+1 lors de l'agrégation par catégorie. */
    @Query("select d from DocumentGenere d left join fetch d.documentType dt left join fetch dt.categorie where d.utilisateur.id = :userId")
    List<DocumentGenere> findByUtilisateurIdWithDocumentTypeAndCategorie(@Param("userId") UUID userId);
}
