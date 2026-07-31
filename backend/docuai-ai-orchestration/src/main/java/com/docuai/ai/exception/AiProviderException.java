package com.docuai.ai.exception;

/** Fournisseur IA indisponible ou en erreur — traduit en 503 côté docuai-api (voir GlobalExceptionHandler). */
public class AiProviderException extends RuntimeException {
    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
