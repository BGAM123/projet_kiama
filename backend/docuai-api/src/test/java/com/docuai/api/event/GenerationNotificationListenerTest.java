package com.docuai.api.event;

import com.docuai.api.service.NotificationService;
import com.docuai.api.service.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Traduction des événements de fin de génération en notification — un cas par issue possible (succès, échec générique, quota IA dépassé). */
@ExtendWith(MockitoExtension.class)
class GenerationNotificationListenerTest {

    @Mock private NotificationService notificationService;

    private GenerationNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new GenerationNotificationListener(notificationService);
    }

    @Test
    void onDocumentGenerationCompleted_success_notifiesSuccess() {
        UUID userId = UUID.randomUUID();
        listener.onDocumentGenerationCompleted(new DocumentGenerationCompletedEvent(UUID.randomUUID(), userId, "Rapport", true, false));

        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        verify(notificationService).create(org.mockito.ArgumentMatchers.eq(userId), typeCaptor.capture(), org.mockito.ArgumentMatchers.anyString());
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.SUCCESS);
    }

    @Test
    void onDocumentGenerationCompleted_genericFailure_notifiesError() {
        UUID userId = UUID.randomUUID();
        listener.onDocumentGenerationCompleted(new DocumentGenerationCompletedEvent(UUID.randomUUID(), userId, "Rapport", false, false));

        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        verify(notificationService).create(org.mockito.ArgumentMatchers.eq(userId), typeCaptor.capture(), org.mockito.ArgumentMatchers.anyString());
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.ERROR);
    }

    @Test
    void onDocumentGenerationCompleted_quotaExceeded_notifiesWarning() {
        UUID userId = UUID.randomUUID();
        listener.onDocumentGenerationCompleted(new DocumentGenerationCompletedEvent(UUID.randomUUID(), userId, "Rapport", false, true));

        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        verify(notificationService).create(org.mockito.ArgumentMatchers.eq(userId), typeCaptor.capture(), org.mockito.ArgumentMatchers.anyString());
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.WARNING);
    }

    @Test
    void onDocumentTypeGenerationCompleted_success_notifiesSuccess() {
        UUID userId = UUID.randomUUID();
        listener.onDocumentTypeGenerationCompleted(new DocumentTypeGenerationCompletedEvent(UUID.randomUUID(), userId, "Rapport d'audit", true, false));

        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        verify(notificationService).create(org.mockito.ArgumentMatchers.eq(userId), typeCaptor.capture(), org.mockito.ArgumentMatchers.anyString());
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.SUCCESS);
    }

    @Test
    void onDocumentTypeGenerationCompleted_quotaExceeded_notifiesWarning() {
        UUID userId = UUID.randomUUID();
        listener.onDocumentTypeGenerationCompleted(new DocumentTypeGenerationCompletedEvent(UUID.randomUUID(), userId, "Rapport d'audit", false, true));

        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        verify(notificationService).create(org.mockito.ArgumentMatchers.eq(userId), typeCaptor.capture(), org.mockito.ArgumentMatchers.anyString());
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.WARNING);
    }
}
