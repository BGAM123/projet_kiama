package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** Catégorie documentaire (regroupement des Documents Types — section 5). */
@Entity
@Table(name = "categorie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Categorie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_categorie", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "description", length = 255)
    private String description;
}
