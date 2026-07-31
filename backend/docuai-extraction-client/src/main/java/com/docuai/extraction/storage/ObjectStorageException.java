package com.docuai.extraction.storage;

/** MinIO indisponible ou en erreur — traduit en 503 côté docuai-api (voir GlobalExceptionHandler). */
public class ObjectStorageException extends RuntimeException {
    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
