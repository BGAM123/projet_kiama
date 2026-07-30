package com.docuai.api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envoi d'e-mails transactionnels (bienvenue, réinitialisation de mot de
 * passe...) via {@link JavaMailSender} — Mailhog en dev (voir
 * docker-compose.yml / application.yml, {@code spring.mail.*}), un vrai
 * relais SMTP en prod (variables {@code SPRING_MAIL_*}).
 * <p>
 * Volontairement "best effort" : une erreur d'envoi est loguée mais ne
 * remonte jamais d'exception à l'appelant. Ce service est invoqué depuis
 * {@link com.docuai.api.event.UserWelcomeEmailListener}, lui-même
 * {@code @Async} — une exception ici finirait de toute façon sur le thread de
 * l'executor sans jamais atteindre la réponse HTTP ; le try/catch sert
 * surtout à obtenir un log exploitable plutôt qu'une stack trace brute.
 */
@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendUrl;

    public MailService(JavaMailSender mailSender,
                        @Value("${docuai.mail.from}") String fromAddress,
                        @Value("${docuai.app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    /**
     * E-mail de bienvenue envoyé après création d'un compte
     * ({@code UserService#create}), contenant les identifiants de connexion
     * initiaux (mot de passe fourni par l'admin ou généré aléatoirement).
     */
    public void sendWelcomeEmail(String to, String firstName, String lastName, String rawPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("Votre compte DocuAI a été créé");
            helper.setText(buildWelcomeBody(firstName, lastName, to, rawPassword), true);
            mailSender.send(message);
            log.info("E-mail de bienvenue envoyé à {}", to);
        } catch (MessagingException | MailException e) {
            log.error("Échec de l'envoi de l'e-mail de bienvenue à {} : {}", to, e.getMessage());
        }
    }

    private String buildWelcomeBody(String firstName, String lastName, String email, String rawPassword) {
        String loginUrl = frontendUrl + "/login";
        return """
                <p>Bonjour %s %s,</p>
                <p>Un compte DocuAI vient d'être créé pour vous. Voici vos identifiants de connexion :</p>
                <ul>
                  <li><strong>E-mail</strong> : %s</li>
                  <li><strong>Mot de passe temporaire</strong> : %s</li>
                </ul>
                <p>Merci de vous connecter et de changer ce mot de passe dès votre première connexion :
                   <a href="%s">%s</a></p>
                <p style="color:#888;font-size:12px">Cet e-mail a été généré automatiquement, merci de ne pas y répondre.</p>
                """.formatted(firstName, lastName, email, rawPassword, loginUrl, loginUrl);
    }
}
