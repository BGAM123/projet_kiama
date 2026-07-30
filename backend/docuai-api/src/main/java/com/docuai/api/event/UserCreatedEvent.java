package com.docuai.api.event;

import java.util.UUID;

/**
 * Publié par {@code UserService#create()} juste après la sauvegarde d'un
 * nouvel utilisateur (et pendant que le mot de passe en clair est encore
 * disponible — il n'est jamais persisté).
 * <p>
 * Écouté de façon transactionnelle + asynchrone par
 * {@link UserWelcomeEmailListener} pour déclencher l'e-mail de bienvenue :
 * <ul>
 *   <li>transactionnel (AFTER_COMMIT) : l'e-mail ne part que si la création
 *       est réellement persistée, jamais en cas de rollback ;</li>
 *   <li>asynchrone : l'envoi SMTP ne bloque jamais la réponse HTTP de
 *       {@code POST /api/v1/users}.</li>
 * </ul>
 */
public record UserCreatedEvent(UUID userId, String email, String firstName, String lastName, String rawPassword) {
}
