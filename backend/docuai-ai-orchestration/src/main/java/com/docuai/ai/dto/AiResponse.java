package com.docuai.ai.dto;

import com.docuai.ai.enums.AiProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResponse {
    /**
     * Le texte ou contenu généré par le modèle.
     */
    private String content;

    /**
     * Le fournisseur qui a effectivement traité la requête.
     */
    private AiProvider usedProvider;

    /**
     * Temps de génération de la réponse.
     */
    private LocalDateTime generatedAt;
    
    /**
     * Optionnel : Le modèle exact utilisé (ex: gpt-3.5-turbo, claude-3-haiku, etc.)
     */
    private String modelName;
}
