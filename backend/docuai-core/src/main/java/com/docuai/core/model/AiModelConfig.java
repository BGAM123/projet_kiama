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
 * d'environnement, "OPENAI_API_KEY") — pas la clé API elle-même. Depuis
 * V14__add_ai_model_config_encrypted_key.sql, une vraie clé peut en plus être
 * saisie depuis l'admin UI et stockée chiffrée dans {@code cleApiChiffree}
 * (AES-256-GCM, voir {@code ApiKeyCipherService} dans docuai-ai-orchestration)
 * — {@code GenerationOrchestrator} la préfère si présente, sinon retombe sur
 * {@code docuai.ai.*} (application.yml / secrets), comme avant. Le déchiffrement
 * exige la clé maîtresse {@code docuai.ai.credentials-encryption-key} : sans
 * elle, une clé stockée en base reste illisible (le fournisseur repasse alors
 * sur la variable d'environnement, si définie).
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

    /** Clé API réelle, chiffrée AES-256-GCM (Base64) — jamais exposée telle quelle en dehors de {@code ApiKeyCipherService}. */
    @Column(name = "cle_api_chiffree", columnDefinition = "TEXT")
    private String cleApiChiffree;

    /** 4 derniers caractères en clair, pour affichage masqué côté UI ("•••• ab12") sans avoir à déchiffrer. */
    @Column(name = "cle_api_apercu", length = 8)
    private String cleApiApercu;

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
