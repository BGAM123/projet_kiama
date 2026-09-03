package com.docuai.api.event;

import java.util.UUID;

/**
 * Publié par {@code DocumentService#generateContent()} une fois la génération
 * IA du contenu d'un document réellement tentée (jamais si l'utilisateur n'a
 * fourni aucune description — dans ce cas aucun appel IA n'a lieu, rien à
 * notifier). {@code success} distingue un contenu effectivement produit d'une
 * dégradation vers un squelette vide après épuisement des tentatives ;
 * {@code quotaExceeded} affine ce second cas quand l'échec vient spécifiquement
 * d'un quota/rate limit IA dépassé (HTTP 429), plutôt que d'une panne
 * générique du fournisseur.
 * <p>
 * Écouté par {@link GenerationNotificationListener} (AFTER_COMMIT + async,
 * même convention que {@link UserCreatedEvent}/{@code UserWelcomeEmailListener}) :
 * la transaction de {@code generateContent} committe toujours (elle ne lève
 * jamais d'exception sur un échec IA, cf. javadoc de la méthode), donc
 * AFTER_COMMIT se déclenche aussi bien pour un succès que pour une dégradation.
 */
public record DocumentGenerationCompletedEvent(
        UUID documentId,
        UUID userId,
        String documentTitle,
        boolean success,
        boolean quotaExceeded) {
}
