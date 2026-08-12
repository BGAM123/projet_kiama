package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Section d'un {@link Document}, créée en une fois à l'aplatissement du
 * squelette du Document Type ({@code DocumentService#create}) puis éditée
 * indépendamment (contenu utilisateur, suggestion IA) via les endpoints
 * {@code /documents/{id}/sections/{sectionId}/**}. {@code userContent} est le
 * contenu réellement retenu (affiché, exporté) ; {@code aiSuggestedContent}
 * une proposition de reformulation en attente de décision utilisateur
 * (accepter -> copiée dans {@code userContent} ; rejeter -> effacée), jamais
 * appliquée automatiquement.
 */
@Entity
@Table(name = "document_section")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class DocumentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_document_section", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_parent_section")
    private DocumentSection parentSection;

    /** Identifiant du {@code StructureNode} d'origine (arbre JSONB du Document Type) — traçabilité uniquement, pas une FK. */
    @Column(name = "source_node_id", length = 100)
    private String sourceNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private DocumentSectionType type;

    @Column(name = "level")
    private Integer level;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    /** Colonnes attendues (copiées du Document Type) pour une section {@code type == TABLE} — informatif, affiché à l'édition. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "table_columns", columnDefinition = "jsonb")
    private List<TableColumnDef> tableColumns;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "user_content", columnDefinition = "TEXT")
    private String userContent;

    @Column(name = "ai_suggested_content", columnDefinition = "TEXT")
    private String aiSuggestedContent;

    /**
     * Confiance (0-100) auto-déclarée par le modèle sur sa dernière suggestion
     * pour cette section — qualifie la sortie IA, jamais le texte saisi à la
     * main : {@code null} tant qu'aucune amélioration n'a été demandée, remis à
     * {@code null} sur réécriture manuelle ou rejet de la suggestion. Ce n'est
     * pas une probabilité calibrée : elle sert de signal de relecture (seuils
     * §3.4 affichés dans l'éditeur), pas de mesure statistique.
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private DocumentSectionStatut statut = DocumentSectionStatut.EMPTY;

    @Column(name = "date_maj", nullable = false)
    @Builder.Default
    private LocalDateTime dateMaj = LocalDateTime.now();
}
