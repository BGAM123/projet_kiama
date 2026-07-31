package com.docuai.extraction.text;

/** Échec de parsing/extraction Tika — traduit en 400 côté docuai-api (fichier invalide/corrompu). */
public class TextExtractionException extends RuntimeException {
    public TextExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
