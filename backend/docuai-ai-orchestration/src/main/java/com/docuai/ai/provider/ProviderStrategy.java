package com.docuai.ai.provider;

import com.docuai.ai.dto.AiRequest;
import com.docuai.ai.dto.AiResponse;
import com.docuai.ai.enums.AiProvider;

/**
 * Interface fondamentale du pattern Strategie pour les fournisseurs d'IA.
 */
public interface ProviderStrategy {
    
    /**
     * Indique quel fournisseur cette stratégie prend en charge.
     * @return Le fournisseur (ex: OPENAI)
     */
    AiProvider getSupportedProvider();

    /**
     * Exécute la requête de génération via le client IA spécifique.
     * @param request La requête unifiée de l'orchestrateur.
     * @return La réponse standardisée.
     */
    AiResponse generate(AiRequest request);
}
