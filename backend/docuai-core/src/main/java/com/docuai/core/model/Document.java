package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Document en édition manuelle assistée — remplace {@link DocumentGenere}
 * (flux conversationnel de génération, retiré). Pas de lien vers {@code
 * Conversation} : un document part directement d'un {@link DocumentType}
 * sélectionné par l'utilisateur ({@code POST /documents}), sans passer par un
 * échange de chat. Le contenu vit dans les lignes {@link DocumentSection}
 * associées (relation 1:N), pas dans une colonne JSONB unique.
 */
@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_document", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document_type", nullable = false)
    private DocumentType documentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur", nullable = false)
    private Utilisateur utilisateur;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatut statut = DocumentStatut.BROUILLON;

    @Enumerated(EnumType.STRING)
    @Column(name = "langue", nullable = false, length = 10)
    @Builder.Default
    private Language langue = Language.FR;

    @Enumerated(EnumType.STRING)
    @Column(name = "ton", nullable = false, length = 30)
    @Builder.Default
    private Tone ton = Tone.NEUTRE;

    /** Clé de l'objet MinIO (bucket-exports) produit par {@code POST /documents/{id}/finalize}. */
    @Column(name = "minio_object_key", length = 500)
    private String minioObjectKey;

    @Column(name = "export_format", length = 10)
    private String exportFormat;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();

    @Column(name = "date_maj")
    private LocalDateTime dateMaj;
}
