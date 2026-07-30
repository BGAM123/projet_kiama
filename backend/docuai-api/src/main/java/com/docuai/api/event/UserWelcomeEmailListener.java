package com.docuai.api.event;

import com.docuai.api.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Déclenche l'e-mail de bienvenue (identifiants initiaux) après création d'un
 * utilisateur — cf. {@link UserCreatedEvent}.
 * <p>
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} : n'est invoqué
 * que si la transaction Spring de {@code UserService#create()} se termine
 * avec succès (aucun envoi si la création finit par échouer/rollback).
 * {@code @Async} : s'exécute sur le pool dédié défini par
 * {@link com.docuai.api.config.AsyncConfig}, sur un thread séparé de celui de
 * la requête HTTP — un Mailhog/serveur SMTP lent ou indisponible ne doit
 * jamais ralentir ni faire échouer {@code POST /api/v1/users}.
 */
@Slf4j
@Component
public class UserWelcomeEmailListener {

    private final MailService mailService;

    public UserWelcomeEmailListener(MailService mailService) {
        this.mailService = mailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        log.debug("Déclenchement de l'e-mail de bienvenue pour l'utilisateur {}", event.userId());
        mailService.sendWelcomeEmail(event.email(), event.firstName(), event.lastName(), event.rawPassword());
    }
}
