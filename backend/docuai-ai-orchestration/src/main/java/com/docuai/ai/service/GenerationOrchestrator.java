package com.docuai.ai.service;

import com.docuai.ai.dto.Chunk;
import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.dto.GenerationResult;
import com.docuai.ai.enums.AiProvider;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.port.AiProviderPort;
import com.docuai.ai.security.ApiKeyCipherService;
import com.docuai.core.model.AiModelConfig;
import com.docuai.core.repository.AiModelConfigRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * Point d'entrée unique du Bloc 5 pour le Bloc 6 (chat/génération) : résout la
 * configuration IA à utiliser (choix explicite ou fournisseur par défaut,
 * {@code ai_model_config.est_defaut}, section 4.3), délègue à l'adaptateur
 * résolu par {@link AiProviderFactory}. Repli sur la configuration par défaut
 * si le fournisseur demandé explicitement n'est pas disponible (clé API
 * absente, adaptateur non branché) — la construction du prompt lui-même
 * (structure attendue, contexte RAG) reste la responsabilité de l'appelant
 * via {@link PromptBuilder}, pas de cet orchestrateur.
 */
@Service
public class GenerationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GenerationOrchestrator.class);

    private final AiProviderFactory providerFactory;
    private final AiModelConfigRepository aiModelConfigRepository;
    private final ApiKeyCipherService apiKeyCipherService;

    public GenerationOrchestrator(AiProviderFactory providerFactory, AiModelConfigRepository aiModelConfigRepository,
                                   ApiKeyCipherService apiKeyCipherService) {
        this.providerFactory = providerFactory;
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.apiKeyCipherService = apiKeyCipherService;
    }

    /**
     * Câblage Resilience4j réel (instances {@code ai-provider} de
     * application.yml, jusque-là orphelines) : appelée depuis des beans
     * externes (ConversationService, GenerationStreamService), donc à travers
     * le proxy Spring AOP — pas d'auto-invocation, {@code @Retry}/
     * {@code @CircuitBreaker} s'appliquent normalement. Le repli de config
     * ({@link #resolveConfig}) est englobé par le retry par simplicité : son
     * coût (throw immédiat, pas d'appel réseau) rend les tentatives
     * supplémentaires inoffensives même si elles n'apportent rien pour une
     * erreur de configuration non transitoire.
     * <p>
     * {@code fallbackMethod} n'est déclaré QUE sur {@code @Retry} (aspect le
     * plus externe, {@code @Retry} encapsule {@code @CircuitBreaker} par
     * défaut) : le mettre aussi sur {@code @CircuitBreaker} déclenche un
     * repli à chaque échec brut de l'adaptateur (pas seulement circuit
     * ouvert), ce qui masque en réalité les tentatives de retry réussies
     * derrière un faux "échec définitif" et double l'emballage d'exception —
     * confirmé empiriquement par {@code GenerationOrchestratorResilienceTest}
     * (log de repli déclenché dès la 1ère tentative malgré un succès en 2e).
     */
    @Retry(name = "ai-provider", fallbackMethod = "generateFallback")
    @CircuitBreaker(name = "ai-provider")
    public GenerationResult generate(GenerationRequest request) {
        ResolvedConfig resolved = resolveConfig(request.getProvider());
        AiProviderPort adapter = providerFactory.resolve(resolved.provider());
        return adapter.generate(withResolvedConfig(request, resolved));
    }

    /**
     * Repli propre après épuisement des tentatives ({@code ai-provider.retry})
     * ou circuit ouvert ({@code ai-provider.circuitbreaker}, exception
     * {@code CallNotPermittedException} — pas une {@link AiProviderException},
     * d'où la signature en {@link Exception}). Convertie en {@link AiProviderException}
     * pour rester dans le contrat déjà géré par les appelants (catch existant
     * dans ConversationService/GenerationStreamService) et déjà mappée en 503
     * par GlobalExceptionHandler — jamais de 500 nu.
     */
    private GenerationResult generateFallback(GenerationRequest request, Exception ex) {
        log.error("Appel IA en échec définitif (après retries ou circuit ouvert) : {}", ex.getMessage());
        throw new AiProviderException("Le service IA est momentanément indisponible après plusieurs tentatives. Réessayez plus tard.", ex);
    }

    /**
     * Même câblage Resilience4j que {@link #generate}, en variante réactive
     * ({@code resilience4j-reactor}, déjà en dépendance) : les aspects
     * {@code @Retry}/{@code @CircuitBreaker} détectent un type de retour
     * {@code Publisher} et enveloppent le {@link Flux} renvoyé avec les
     * opérateurs réactifs correspondants plutôt que d'appliquer une boucle
     * bloquante — chaque échec signalé sur le canal d'erreur du flux (panne
     * réseau pendant le streaming SSE côté fournisseur, cf.
     * {@code AbstractOpenAiStyleAdapter#streamGenerate}) déclenche une
     * re-souscription, exactement comme un appel bloquant raté redéclenche
     * une tentative.
     * <p>
     * Limite assumée : {@link #resolveConfig} s'exécute de façon synchrone
     * AVANT la construction du {@code Flux} (contrairement à l'appel HTTP
     * réactif lui-même, différé jusqu'à la souscription) — une exception levée
     * à ce stade (config IA absente/inconnue) échappe donc à l'enveloppe
     * réactive de Resilience4j (rien à ré-essayer, pas encore de Publisher) et
     * remonte directement à l'appelant. Sans conséquence pratique : c'est le
     * même type d'erreur non transitoire que le repli de {@link #generate}
     * traite déjà sans bénéfice réel du retry, et {@link GenerationStreamService}
     * catch {@link AiProviderException} quel que soit le chemin.
     */
    @Retry(name = "ai-provider", fallbackMethod = "streamGenerateFallback")
    @CircuitBreaker(name = "ai-provider")
    public Flux<Chunk> streamGenerate(GenerationRequest request) {
        ResolvedConfig resolved = resolveConfig(request.getProvider());
        AiProviderPort adapter = providerFactory.resolve(resolved.provider());
        return adapter.streamGenerate(withResolvedConfig(request, resolved));
    }

    /** Repli réactif — mêmes raisons qu'en {@link #generateFallback}, propagé via le canal d'erreur du Flux plutôt que par exception directe. */
    private Flux<Chunk> streamGenerateFallback(GenerationRequest request, Exception ex) {
        log.error("Flux IA en échec définitif (après retries ou circuit ouvert) : {}", ex.getMessage());
        return Flux.error(new AiProviderException("Le service IA est momentanément indisponible après plusieurs tentatives. Réessayez plus tard.", ex));
    }

    private ResolvedConfig resolveConfig(String explicitProvider) {
        if (explicitProvider != null && !explicitProvider.isBlank()) {
            AiProvider provider = parseProvider(explicitProvider);
            if (providerFactory.isAvailable(provider)) {
                Optional<AiModelConfig> config = aiModelConfigRepository.findByFournisseurAndActifTrue(provider.name()).stream().findFirst();
                return toResolvedConfig(provider, config.orElse(null));
            }
            log.warn("Fournisseur IA demandé explicitement indisponible ({}), repli sur le fournisseur par défaut.", provider);
        }
        AiModelConfig defaultConfig = aiModelConfigRepository.findByEstDefautTrueAndActifTrue()
                .orElseThrow(() -> new AiProviderException(
                        "Aucune configuration IA par défaut active (ai_model_config.est_defaut) — configurez-en une via /api/v1/ai-configs."));
        AiProvider provider = parseProvider(defaultConfig.getFournisseur());
        if (!providerFactory.isAvailable(provider)) {
            throw new AiProviderException("Le fournisseur IA par défaut (" + provider
                    + ") n'est pas disponible (clé API absente ou adaptateur non branché).");
        }
        return toResolvedConfig(provider, defaultConfig);
    }

    /**
     * Déchiffre la clé API stockée pour {@code config} si elle existe et que la
     * clé maîtresse ({@code docuai.ai.credentials-encryption-key}) est
     * configurée — sinon {@code apiKeyOverride} reste null et l'adaptateur
     * retombe sur sa clé issue de {@code docuai.ai.*} (variable d'environnement).
     */
    private ResolvedConfig toResolvedConfig(AiProvider provider, AiModelConfig config) {
        String model = config != null ? config.getNomModele() : null;
        String apiKeyOverride = null;
        if (config != null && config.getCleApiChiffree() != null && !config.getCleApiChiffree().isBlank()
                && apiKeyCipherService.isConfigured()) {
            apiKeyOverride = apiKeyCipherService.decrypt(config.getCleApiChiffree());
        }
        return new ResolvedConfig(provider, model, apiKeyOverride);
    }

    private AiProvider parseProvider(String raw) {
        try {
            return AiProvider.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AiProviderException("Fournisseur IA inconnu : " + raw);
        }
    }

    private GenerationRequest withResolvedConfig(GenerationRequest request, ResolvedConfig resolved) {
        boolean hasModel = request.getModel() != null && !request.getModel().isBlank();
        return request.toBuilder()
                .provider(resolved.provider().name())
                .model(hasModel ? request.getModel() : resolved.model())
                .apiKeyOverride(resolved.apiKeyOverride())
                .build();
    }

    private record ResolvedConfig(AiProvider provider, String model, String apiKeyOverride) {}
}
