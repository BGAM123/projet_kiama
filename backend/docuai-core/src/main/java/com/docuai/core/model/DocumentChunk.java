package com.docuai.core.model;

import com.docuai.core.model.support.PgVectorType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fragment indexé d'un document de référence (RAG, Bloc 5) — {@code idReference}
 * reste un UUID brut plutôt qu'une relation JPA vers {@code DocumentReference}
 * : cette entité n'existe pas encore (introduite au Bloc 6, conversations),
 * seule la table {@code document_reference} existe déjà (V1__init_schema.sql).
 * {@code embedding} est mappé via {@link PgVectorType} (pgvector, colonne
 * {@code vector(1536)}, dimension du modèle {@code text-embedding-3-small}
 * d'OpenAI utilisé par {@code EmbeddingService}).
 */
@Entity
@Table(name = "document_chunk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_chunk", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "id_reference", nullable = false)
    private UUID idReference;

    @Column(name = "index_chunk", nullable = false)
    private Integer indexChunk;

    @Column(name = "contenu", nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Type(PgVectorType.class)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
