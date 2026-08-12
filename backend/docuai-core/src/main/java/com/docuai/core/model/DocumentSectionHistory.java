package com.docuai.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Instantané d'un {@link DocumentSection#getUserContent()} au moment d'une
 * sauvegarde utilisateur ({@code source = USER}) ou d'une application de
 * suggestion IA ({@code source = AI_APPLIED}) — permet de mesurer l'usage
 * réel de la fonctionnalité d'amélioration (accepté vs rejeté, tracé par
 * ailleurs dans les logs structurés) et offre un historique de relecture.
 */
@Entity
@Table(name = "document_section_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class DocumentSectionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_history", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document_section", nullable = false)
    private DocumentSection documentSection;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private DocumentSectionHistorySource source;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
