package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Configuration d'un modèle IA exploitable (fournisseur + modèle), pilotée
 * par le Bloc 5 (résolution du fournisseur par défaut) et exposée en CRUD par
 * {@code /api/v1/ai-configs} au Bloc 8.
 * <p>
 * {@code fournisseur} reste un {@code String} brut (et non l'énum
 * {@code AiProvider}) : cette entité vit dans docuai-core (domaine), qui ne
 * dépend pas de docuai-ai-orchestration (infrastructure IA — "hexagonale
 * simplifiée", voir ARCHITECTURE.md) ; {@code AiProviderFactory}/
 * {@code GenerationOrchestrator} convertissent via
 * {@code AiProvider.valueOf(...)}, les valeurs étant alignées avec la
 * contrainte {@code chk_ai_fournisseur} de V1__init_schema.sql.
 * <p>
 * {@code referenceCleApi} est un POINTEUR (ex. nom de variable
 * d'environnement, "OPENAI_API_KEY") — jamais la clé API elle-même, qui
 * reste lue depuis {@code docuai.ai.*} (application.yml / secrets), pas
 * depuis la base (voir V2__seed_roles_permissions.sql pour des exemples).
 */
@Entity
@Table(name = "ai_model_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AiModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_config", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "fournisseur", nullable = false, length = 30)
    private String fournisseur;

    @Column(name = "nom_modele", nullable = false, length = 100)
    private String nomModele;

    @Column(name = "reference_cle_api", length = 255)
    private String referenceCleApi;

    @Column(name = "est_defaut", nullable = false)
    @Builder.Default
    private Boolean estDefaut = false;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
