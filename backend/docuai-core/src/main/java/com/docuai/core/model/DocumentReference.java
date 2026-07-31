package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Document de référence importé dans une {@link Conversation} — sa version
 * indexée pour le RAG vit dans {@code document_chunk}
 * ({@link DocumentChunk#getIdReference()}, UUID brut plutôt qu'une relation
 * JPA : DocumentChunk vit dans le module docuai-ai-orchestration/RAG, qui ne
 * dépend pas de cette entité "conversation").
 */
@Entity
@Table(name = "document_reference")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class DocumentReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_reference", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_conversation")
    private Conversation conversation;

    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    @Column(name = "chemin_stockage", nullable = false, length = 500)
    private String cheminStockage;

    @Column(name = "date_import", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateImport = LocalDateTime.now();
}
