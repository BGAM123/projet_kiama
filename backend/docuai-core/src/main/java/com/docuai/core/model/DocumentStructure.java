package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * Arbre de structure extrait (ou corrigé manuellement) d'un Document Type —
 * relation 1:1 avec {@link DocumentType} (contrainte {@code UNIQUE} sur
 * {@code id_document_type} en base, cf. V1__init_schema.sql).
 * <p>
 * {@code arbreJson} est mappé nativement en JSONB via
 * {@code @JdbcTypeCode(SqlTypes.JSON)} — fonctionnalité intégrée à Hibernate
 * ORM 6 (fourni par spring-boot-starter-data-jpa 3.3.x), donc pas besoin de
 * la dépendance tierce {@code hibernate-types} qu'exigeait Hibernate 5.
 * Hibernate (dé)sérialise la liste via l'{@code ObjectMapper} Jackson présent
 * sur le classpath runtime de docuai-api (spring-boot-starter-web) — aucune
 * dépendance Jackson supplémentaire requise dans docuai-core lui-même.
 */
@Entity
@Table(name = "document_structure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class DocumentStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_structure", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document_type", unique = true)
    private DocumentType documentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arbre_json", nullable = false, columnDefinition = "jsonb")
    private List<StructureNode> arbreJson;

    @Column(name = "possede_toc", nullable = false)
    @Builder.Default
    private Boolean possedeToc = false;

    /**
     * En-tête/pied de page extraits du fichier source (DOCX uniquement) —
     * texte statique répété tel quel sur chaque page à l'export (Bloc 7),
     * jamais passé au fournisseur IA (Bloc 6, contrairement à
     * {@code arbreJson} qui, lui, sert de structure attendue au prompt).
     */
    @Column(name = "header_text", columnDefinition = "text")
    private String headerText;

    @Column(name = "footer_text", columnDefinition = "text")
    private String footerText;

    /**
     * Origine de la structure : {@code IMPORTED} (extraction déterministe
     * d'un fichier importé, comportement historique) ou {@code AI_GENERATED}
     * (flux "décrire en texte -> squelette généré par IA"). Colonne ajoutée
     * par V8__document_structure_ai_generation_source.sql.
     */
    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private String source = "IMPORTED";
}
