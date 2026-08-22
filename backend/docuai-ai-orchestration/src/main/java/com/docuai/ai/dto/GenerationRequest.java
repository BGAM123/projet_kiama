package com.docuai.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Requête de génération/chat envoyée à un {@link com.docuai.ai.port.AiProviderPort}.
 * Construite par {@link com.docuai.ai.service.GenerationOrchestrator} à partir
 * de {@link com.docuai.ai.service.PromptBuilder} (structure attendue + contexte
 * RAG déjà fondus dans {@code systemPrompt}/{@code userPrompt} — les adaptateurs
 * n'ont pas besoin de connaître {@code DocumentChunk}/pgvector).
 * {@code provider}/{@code model} sont résolus par l'orchestrateur si absents
 * (fournisseur par défaut, {@code ai_model_config.est_defaut}).
 * {@code apiKeyOverride} est renseigné par l'orchestrateur quand une clé réelle
 * est stockée (chiffrée) pour la configuration résolue — les adaptateurs
 * l'utilisent en priorité sur leur clé issue de {@code docuai.ai.*} (repli).
 */
@Getter
@Builder(toBuilder = true)
public class GenerationRequest {
    private final String provider;
    private final String model;
    private final String systemPrompt;
    private final String userPrompt;
    @Builder.Default
    private final List<ChatMessage> history = List.of();
    private final Double temperature;
    private final Integer maxOutputTokens;
    private final String apiKeyOverride;
}
