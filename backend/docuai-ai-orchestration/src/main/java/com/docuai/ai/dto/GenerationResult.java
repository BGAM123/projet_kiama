package com.docuai.ai.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Résultat d'un appel bloquant à {@link com.docuai.ai.port.AiProviderPort#generate}.
 * Ne porte pas de verdict de conformité structurelle : les adaptateurs
 * ignorent la structure attendue du document (séparation des responsabilités)
 * — c'est à l'appelant (Bloc 6) d'invoquer {@code StructuralValidator#validate}
 * sur {@code content} une fois le résultat obtenu.
 */
@Getter
@Builder
public class GenerationResult {
    private final String content;
    private final String provider;
    private final String model;
    private final Integer promptTokens;
    private final Integer completionTokens;
}
