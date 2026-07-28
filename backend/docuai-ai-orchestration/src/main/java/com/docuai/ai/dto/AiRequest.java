package com.docuai.ai.dto;

import com.docuai.ai.enums.AiProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRequest {
    /**
     * Le message de l'utilisateur (le prompt principal).
     */
    private String prompt;

    /**
     * Optionnel: Le contexte système ou les instructions (System prompt).
     */
    private String systemPrompt;

    /**
     * Optionnel: Le fournisseur d'IA souhaité pour cette requête.
     * Si null, l'orchestrateur utilisera le fournisseur par défaut.
     */
    private AiProvider providerPreference;

    /**
     * Optionnel: Température de génération (0.0 = précis/déterministe, 1.0 = créatif).
     */
    private Double temperature;
}
