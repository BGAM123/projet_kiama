package com.docuai.api.service;

import com.docuai.api.mapper.NotificationMapper;
import com.docuai.core.model.Notification;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.NotificationRepository;
import com.docuai.core.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@link NotificationService#create} : seul producteur actuel, {@code GenerationNotificationListener}. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private UtilisateurRepository utilisateurRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, notificationMapper, utilisateurRepository);
    }

    @Test
    void create_persistsNotification_withTypeAndContent() {
        UUID userId = UUID.randomUUID();
        Utilisateur reference = Utilisateur.builder().id(userId).build();
        when(utilisateurRepository.getReferenceById(userId)).thenReturn(reference);

        service.create(userId, NotificationType.SUCCESS, "Génération terminée.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUtilisateur()).isEqualTo(reference);
        assertThat(captor.getValue().getType()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().getContenu()).isEqualTo("Génération terminée.");
        assertThat(captor.getValue().getLue()).isFalse();
    }

    /** N'arrive qu'en test (utilisateur construit sans id) — ne doit jamais tenter d'insertion sans destinataire. */
    @Test
    void create_doesNothing_whenUserIdIsNull() {
        service.create(null, NotificationType.ERROR, "peu importe");

        verifyNoInteractions(notificationRepository);
        verify(utilisateurRepository, never()).getReferenceById(any());
    }
}
