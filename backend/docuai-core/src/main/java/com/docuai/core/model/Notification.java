package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification applicative (table {@code notification}, section 5/6 — permission
 * {@code NOTIFICATION_READ_OWN}). La table existe depuis V1__init_schema.sql
 * mais n'avait encore aucune entité/contrôleur associé ; les notifications
 * elles-mêmes restent aujourd'hui alimentées manuellement (aucun producteur
 * métier ne les insère encore), seule la consultation/lecture est couverte
 * ici.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_notification", updatable = false, nullable = false)
    private UUID id;

    // Nullable en base (pas de NOT NULL sur id_utilisateur dans le schéma) :
    // conservé nullable ici pour rester fidèle à V1__init_schema.sql plutôt
    // que d'introduire une contrainte non présente en base.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "contenu", nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Column(name = "lue", nullable = false)
    @Builder.Default
    private Boolean lue = false;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
