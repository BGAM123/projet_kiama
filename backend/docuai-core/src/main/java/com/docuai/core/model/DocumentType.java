package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Document Type — modèle documentaire importé puis structuré (section 5).
 * {@code fichierSourceCle} et le pipeline d'extraction (statut, etc.) sont
 * pilotés par le Bloc 4 ; ce Bloc 3 n'expose que le CRUD référentiel
 * (nom/description/catégorie) via {@code DocumentTypeService}.
 */
@Entity
@Table(name = "document_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_document_type", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nom", nullable = false, length = 150)
    private String nom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categorie")
    private Categorie categorie;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    @Builder.Default
    private DocumentTypeStatut statut = DocumentTypeStatut.IMPORTE;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur_createur")
    private Utilisateur utilisateurCreateur;

    /** Clé de l'objet MinIO du fichier source importé (Bloc 4). */
    @Column(name = "fichier_source_cle", length = 500)
    private String fichierSourceCle;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
