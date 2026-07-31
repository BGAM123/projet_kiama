package com.docuai.api.service;

import com.docuai.api.dto.NotificationDTO;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.NotificationMapper;
import com.docuai.core.model.Notification;
import com.docuai.core.repository.NotificationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service applicatif : consultation et marquage "lue" des notifications
 * (section 5/6, permission {@code NOTIFICATION_READ_OWN}). Aucun producteur
 * métier n'insère encore de notifications à ce stade (Blocs 6/9) — seule la
 * lecture des notifications déjà présentes en base est couverte.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationRepository notificationRepository, NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> listForUser(UUID userId) {
        return notificationMapper.toDtoList(notificationRepository.findByUtilisateur_IdOrderByDateCreationDesc(userId));
    }

    @Transactional
    public void markRead(UUID id, UUID requestingUserId, boolean isAdmin) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "Notification introuvable."));
        UUID ownerId = notification.getUtilisateur() != null ? notification.getUtilisateur().getId() : null;
        if (!isAdmin && (ownerId == null || !ownerId.equals(requestingUserId))) {
            throw new AccessDeniedException("Cette notification ne vous appartient pas.");
        }
        notification.setLue(true);
        notificationRepository.save(notification);
    }
}
