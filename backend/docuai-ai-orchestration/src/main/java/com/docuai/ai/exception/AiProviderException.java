package com.docuai.ai.exception;

import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Fournisseur IA indisponible ou en erreur — traduit en 503 côté docuai-api (voir GlobalExceptionHandler). */
public class AiProviderException extends RuntimeException {
    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Vrai si cette erreur (ou l'une de ses causes, ex. réenveloppée par
     * {@code GenerationOrchestrator} après épuisement des tentatives Resilience4j)
     * vient d'une réponse HTTP 429 du fournisseur — code universellement utilisé
     * par les API compatibles OpenAI et par Anthropic/Gemini pour signaler un
     * quota/rate limit dépassé ("plus de tokens disponibles"), sans avoir à
     * parser le format d'erreur propre à chaque fournisseur.
     */
    public boolean isQuotaExceeded() {
        for (Throwable current = this; current != null; current = current.getCause()) {
            if (current instanceof WebClientResponseException webClientException
                    && webClientException.getStatusCode().value() == 429) {
                return true;
            }
        }
        return false;
    }
}
