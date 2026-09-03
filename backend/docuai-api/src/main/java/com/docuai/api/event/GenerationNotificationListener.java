package com.docuai.api.event;

import com.docuai.api.service.NotificationService;
import com.docuai.api.service.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduit la fin d'une génération IA (document ou Document Type) en
 * notification applicative — même convention que {@link UserCreatedEvent}/
 * {@code UserWelcomeEmailListener} : {@code AFTER_COMMIT} (la notification ne
 * doit refléter que ce qui est réellement persisté) + {@code @Async} (l'écriture
 * de la notification ne doit jamais retarder la réponse HTTP de génération,
 * déjà potentiellement longue à cause de l'appel IA lui-même).
 */
@Slf4j
@Component
public class GenerationNotificationListener {

    private final NotificationService notificationService;

    public GenerationNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentGenerationCompleted(DocumentGenerationCompletedEvent event) {
        String title = event.documentTitle() == null || event.documentTitle().isBlank() ? "Document" : event.documentTitle();
        if (event.success()) {
            notificationService.create(event.userId(), NotificationType.SUCCESS,
                    "La génération du contenu de « " + title + " » est terminée.");
        } else if (event.quotaExceeded()) {
            notificationService.create(event.userId(), NotificationType.WARNING,
                    "Le fournisseur IA n'a plus de quota disponible — la génération du contenu de « " + title
                            + " » n'a pas pu aboutir. Le document a été créé avec un squelette vide, à compléter manuellement ou à régénérer plus tard.");
        } else {
            notificationService.create(event.userId(), NotificationType.ERROR,
                    "La génération du contenu de « " + title
                            + " » a échoué après plusieurs tentatives. Le document a été créé avec un squelette vide.");
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentTypeGenerationCompleted(DocumentTypeGenerationCompletedEvent event) {
        String name = event.documentTypeName() == null || event.documentTypeName().isBlank() ? "Document Type" : event.documentTypeName();
        if (event.success()) {
            notificationService.create(event.userId(), NotificationType.SUCCESS,
                    "Le squelette du Document Type « " + name + " » a été généré avec succès — relisez-le avant de l'activer.");
        } else if (event.quotaExceeded()) {
            notificationService.create(event.userId(), NotificationType.WARNING,
                    "Le fournisseur IA n'a plus de quota disponible — la génération du squelette de « " + name + " » a échoué.");
        } else {
            notificationService.create(event.userId(), NotificationType.ERROR,
                    "La génération du squelette de « " + name + " » a échoué après plusieurs tentatives.");
        }
    }
}
