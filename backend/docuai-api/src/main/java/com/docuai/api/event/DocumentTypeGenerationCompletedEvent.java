package com.docuai.api.event;

import java.util.UUID;

/**
 * Publié par {@code DocumentTypeGenerationService#generate()} après le flux
 * "décrire en texte -> squelette généré par IA" (toujours tenté ici,
 * contrairement à {@link DocumentGenerationCompletedEvent} : une description
 * est obligatoire pour créer un Document Type par ce flux). {@code success}
 * vaut vrai pour {@code STRUCTURE_EXTRAITE}, faux pour {@code ECHEC_EXTRACTION} ;
 * {@code quotaExceeded} affine l'échec quand il vient d'un quota IA dépassé.
 */
public record DocumentTypeGenerationCompletedEvent(
        UUID documentTypeId,
        UUID userId,
        String documentTypeName,
        boolean success,
        boolean quotaExceeded) {
}
