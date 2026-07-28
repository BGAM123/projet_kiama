package com.docuai.api.service;

import com.docuai.core.model.Notification;
import com.docuai.core.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {
    
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    
    private final NotificationRepository repository;
    private final JavaMailSender mailSender;

    public NotificationService(NotificationRepository repository, JavaMailSender mailSender) {
        this.repository = repository;
        this.mailSender = mailSender;
    }

    /**
     * Crée une notification in-app et envoie un email simultanément.
     */
    public void createAndSendNotification(Long userId, String userEmail, String title, String message) {
        // Enregistrement de l'alerte dans la base pour le Dashboard
        Notification notif = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .build();
        repository.save(notif);

        // Envoi d'Email
        if (userEmail != null && !userEmail.isEmpty()) {
            try {
                SimpleMailMessage mailMessage = new SimpleMailMessage();
                mailMessage.setTo(userEmail);
                mailMessage.setSubject(title);
                mailMessage.setText(message);
                mailSender.send(mailMessage);
                log.info("Email envoyé à {}", userEmail);
            } catch (Exception e) {
                log.error("Échec de l'envoi de l'email de notification à {}", userEmail, e);
            }
        }
    }

    public List<Notification> getUserNotifications(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    public void markAsRead(Long notificationId) {
        repository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            repository.save(n);
        });
    }
}
