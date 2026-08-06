package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Document généré par IA (Bloc 6, section 4.4/5). Deux colonnes de
 * V1__init_schema.sql sont volontairement NON mappées ici :
 * <ul>
 *   <li>{@code contenu_pivot} (JSONB, "pivot" structuré du Content Assembler) —
 *   pas encore de représentation concrète nécessaire pour ce bloc, réservé à
 *   une itération future (Hibernate {@code ddl-auto: validate} n'exige pas
 *   qu'une colonne existante soit mappée par une entité) ;</li>
 *   <li>{@code versions_historique} (JSONB) — non exposé par le contrat
 *   frontend {@code GeneratedDocument}, non nécessaire à ce stade.</li>
 * </ul>
 * {@code promptUtilisateur} porte ce que le frontend nomme {@code contentPivot}
 * (le texte/les instructions saisis par l'utilisateur pour lancer la
 * génération, cf. {@code StartGenerationRequest}) — nommage distinct du champ
 * DB {@code contenu_pivot} ci-dessus, qui désigne un concept différent (le
 * pivot assemblé, pas la saisie utilisateur) ; voir le commentaire de
 * {@code document_genere} dans V1__init_schema.sql pour le détail de cet
 * écart de vocabulaire assumé.
 */
@Entity
@Table(name = "document_genere")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class DocumentGenere {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_document_genere", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_conversation")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document_type")
    private DocumentType documentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    @Builder.Default
    private DocumentGenereStatut statut = DocumentGenereStatut.BROUILLON;

    @Enumerated(EnumType.STRING)
    @Column(name = "langue", nullable = false, length = 10)
    @Builder.Default
    private Language langue = Language.FR;

    @Enumerated(EnumType.STRING)
    @Column(name = "ton", nullable = false, length = 30)
    @Builder.Default
    private Tone ton = Tone.NEUTRE;

    @Enumerated(EnumType.STRING)
    @Column(name = "longueur_cible", length = 20)
    private TargetLength longueurCible;

    @Column(name = "prompt_utilisateur", columnDefinition = "TEXT")
    private String promptUtilisateur;

    @Column(name = "contenu", columnDefinition = "TEXT")
    private String contenu;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sections", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<GenerationSectionNode> sections = new ArrayList<>();

    /** Clé de l'objet MinIO (bucket-exports) produit par l'export automatique en fin de génération réussie — voir V6__add_document_genere_export.sql. */
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
