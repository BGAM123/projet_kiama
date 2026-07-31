package com.docuai.ai.service;

import com.docuai.ai.dto.Chunk;
import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.dto.GenerationResult;
import com.docuai.ai.enums.AiProvider;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.port.AiProviderPort;
import com.docuai.core.model.AiModelConfig;
import com.docuai.core.repository.AiModelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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

    public GenerationOrchestrator(AiProviderFactory providerFactory, AiModelConfigRepository aiModelConfigRepository) {
        this.providerFactory = providerFactory;
        this.aiModelConfigRepository = aiModelConfigRepository;
    }

    public GenerationResult generate(GenerationRequest request) {
        ResolvedConfig resolved = resolveConfig(request.getProvider());
        AiProviderPort adapter = providerFactory.resolve(resolved.provider());
        return adapter.generate(withResolvedConfig(request, resolved));
    }

    public Flux<Chunk> streamGenerate(GenerationRequest request) {
        ResolvedConfig resolved = resolveConfig(request.getProvider());
        AiProviderPort adapter = providerFactory.resolve(resolved.provider());
        return adapter.streamGenerate(withResolvedConfig(request, resolved));
    }

    private ResolvedConfig resolveConfig(String explicitProvider) {
        if (explicitProvider != null && !explicitProvider.isBlank()) {
            AiProvider provider = parseProvider(explicitProvider);
            if (providerFactory.isAvailable(provider)) {
                String model = aiModelConfigRepository.findByFournisseurAndActifTrue(provider.name()).stream()
                        .findFirst().map(AiModelConfig::getNomModele).orElse(null);
                return new ResolvedConfig(provider, model);
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
        return new ResolvedConfig(provider, defaultConfig.getNomModele());
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
                .build();
    }

    private record ResolvedConfig(AiProvider provider, String model) {}
}
