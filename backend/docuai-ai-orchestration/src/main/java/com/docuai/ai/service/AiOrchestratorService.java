package com.docuai.ai.service;

import com.docuai.ai.dto.AiRequest;
import com.docuai.ai.dto.AiResponse;
import com.docuai.ai.enums.AiProvider;
import com.docuai.ai.provider.ProviderStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestratorService.class);
    private final Map<AiProvider, ProviderStrategy> strategies;

    public AiOrchestratorService(List<ProviderStrategy> strategyList) {
        log.info("Initialisation de l'Orchestrateur IA avec {} fournisseurs actifs.", strategyList.size());
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(ProviderStrategy::getSupportedProvider, strategy -> strategy));
    }

    public AiResponse generate(AiRequest request) {
        if (strategies.isEmpty()) {
            throw new RuntimeException("Aucun fournisseur IA n'a été initialisé (vérifiez vos clés d'API / base-url).");
        }

        AiProvider targetProvider = request.getProviderPreference() != null 
                ? request.getProviderPreference() 
                : AiProvider.OPENAI; 

        ProviderStrategy strategy = strategies.get(targetProvider);
        
        if (strategy == null) {
            log.warn("Le fournisseur {} n'est pas disponible. Tentative de fallback...", targetProvider);
            // Fallback sur le premier fournisseur disponible
            strategy = strategies.values().stream().findFirst().get();
            log.info("Fallback sur le fournisseur : {}", strategy.getSupportedProvider());
        }

        log.info("Génération de la réponse en utilisant le fournisseur : {}", strategy.getSupportedProvider());
        return strategy.generate(request);
    }
}
