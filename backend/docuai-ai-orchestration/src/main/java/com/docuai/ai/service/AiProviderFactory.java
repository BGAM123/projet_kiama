package com.docuai.ai.service;

import com.docuai.ai.enums.AiProvider;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.port.AiProviderPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Résout l'adaptateur {@link AiProviderPort} à utiliser pour un
 * {@link AiProvider} donné, par auto-découverte Spring : chaque bean
 * {@code @Component} implémentant {@code AiProviderPort} s'enregistre lui-même
 * via son {@code provider()} (voir README du module, "Ajouter un nouveau
 * fournisseur IA"). Les fournisseurs "structurés mais non branchés par
 * défaut" (Gemini/Mistral/DeepSeek — pas de {@code @Component}) sont donc
 * absents de cette table et {@link #resolve} le signale explicitement plutôt
 * que de lever une NPE.
 */
@Service
public class AiProviderFactory {

    private final Map<AiProvider, AiProviderPort> adaptersByProvider;

    public AiProviderFactory(List<AiProviderPort> adapters) {
        this.adaptersByProvider = adapters.stream()
                .collect(Collectors.toMap(AiProviderPort::provider, Function.identity()));
    }

    public AiProviderPort resolve(AiProvider provider) {
        AiProviderPort adapter = adaptersByProvider.get(provider);
        if (adapter == null) {
            throw new AiProviderException("Aucun adaptateur IA actif pour le fournisseur " + provider
                    + " (non branché par défaut — voir docuai-ai-orchestration/README.md).");
        }
        return adapter;
    }

    public boolean isAvailable(AiProvider provider) {
        return adaptersByProvider.containsKey(provider);
    }
}
