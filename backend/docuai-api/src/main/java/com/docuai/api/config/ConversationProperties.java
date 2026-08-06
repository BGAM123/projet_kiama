package com.docuai.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Liaison de docuai.conversation.* (application.yml) — fenêtre de contexte
 * envoyée au LLM à chaque tour (ConversationService#generateAssistantReply).
 * {@code keepLastMessages} borne ce qui part en clair (historique verbatim) ;
 * au-delà, les messages plus anciens sont condensés dans
 * {@code Conversation#contextSummary} par lots de {@code summaryBatchSize}
 * (amorti : pas un appel LLM de résumé à chaque tour).
 */
@ConfigurationProperties(prefix = "docuai.conversation")
@Getter
@Setter
public class ConversationProperties {
    private int keepLastMessages = 10;
    private int summaryBatchSize = 6;
}
