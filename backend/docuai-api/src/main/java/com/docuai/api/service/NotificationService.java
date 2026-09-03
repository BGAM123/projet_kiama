package com.docuai.api.service;

import com.docuai.api.dto.NotificationDTO;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.NotificationMapper;
import com.docuai.core.model.Notification;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.NotificationRepository;
import com.docuai.core.repository.UtilisateurRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service applicatif : consultation, marquage "lue" et création des
 * notifications (section 5/6, permission {@code NOTIFICATION_READ_OWN}).
 * Producteurs métier actuels : {@code GenerationNotificationListener} (fin de
 * génération IA — document ou Document Type, succès ou échec).
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final UtilisateurRepository utilisateurRepository;

    public NotificationService(NotificationRepository notificationRepository, NotificationMapper notificationMapper,
                                UtilisateurRepository utilisateurRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * Sans effet si {@code userId} est {@code null} : ne devrait arriver qu'en
     * test (utilisateur construit sans id) — en production, chaque producteur
     * appelle cette méthode avec l'id de l'utilisateur réellement à l'origine
     * de l'action génératrice.
     */
    @Transactional
    public void create(UUID userId, NotificationType type, String content) {
        if (userId == null) {
            return;
        }
        // getReferenceById : proxy JPA, pas de SELECT préalable — seul le FK
        // id_utilisateur de la ligne insérée en a besoin.
        Utilisateur reference = utilisateurRepository.getReferenceById(userId);
        Notification notification = Notification.builder()
                .utilisateur(reference)
                .type(type.name())
                .contenu(content)
                .build();
        notificationRepository.save(notification);
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
