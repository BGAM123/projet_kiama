package com.docuai.core.repository;

import com.docuai.core.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByIdReference(UUID idReference);

    void deleteByIdReference(UUID idReference);

    /**
     * Recherche par similarité cosinus (opérateur pgvector {@code <=>}, plus
     * petit = plus proche) parmi les chunks des documents de référence donnés.
     * {@code embeddingLiteral} est la représentation texte pgvector
     * ("[0.12,-0.03,...]", cf. {@code PgVectorType#format}) castée côté SQL :
     * un paramètre de {@code @Query} natif est bindé en texte simple (pas de
     * dépendance à l'enregistrement du type PGvector sur la connexion JDBC,
     * contrairement au mapping d'une colonne d'entité).
     */
    @Query(value = """
            SELECT * FROM document_chunk
            WHERE id_reference IN (:referenceIds)
            ORDER BY embedding <=> CAST(:embeddingLiteral AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<DocumentChunk> findNearest(@Param("referenceIds") List<UUID> referenceIds,
                                     @Param("embeddingLiteral") String embeddingLiteral,
                                     @Param("topK") int topK);
}
