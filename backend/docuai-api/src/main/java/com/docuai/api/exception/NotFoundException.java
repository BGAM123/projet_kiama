package com.docuai.api.exception;

import lombok.Getter;

/** Ressource introuvable (404) — catégorie, utilisateur, rôle, etc. */
@Getter
public class NotFoundException extends RuntimeException {
    private final String code;

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }
}
