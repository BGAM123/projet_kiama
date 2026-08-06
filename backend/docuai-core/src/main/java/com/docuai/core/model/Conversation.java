package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Fil de discussion IA rattaché à un Document Type cible (Bloc 6, section 5). */
@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_conversation", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document_type")
    private DocumentType documentType;

    @Column(name = "titre", length = 255)
    private String titre;

    /** Résumé glissant des messages sortis de la fenêtre récente (ConversationService) — voir V5__add_conversation_context_summary.sql. */
    @Column(name = "context_summary", columnDefinition = "TEXT")
    private String contextSummary;

    @Column(name = "context_summary_upto_message_id")
    private UUID contextSummaryUpToMessageId;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
