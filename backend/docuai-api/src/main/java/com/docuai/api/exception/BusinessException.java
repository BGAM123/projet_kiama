package com.docuai.api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Erreur métier générique portant son propre code et statut HTTP — ex.
 * suppression d'un Document Type utilisé (409, DOCUMENT_TYPE_IN_USE, règle de
 * gestion de la section 6 du prompt maître), ou fournisseur IA indisponible
 * (503, AI_PROVIDER_UNAVAILABLE, section 9).
 */
@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException serviceUnavailable(String code, String message) {
        return new BusinessException(code, message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
