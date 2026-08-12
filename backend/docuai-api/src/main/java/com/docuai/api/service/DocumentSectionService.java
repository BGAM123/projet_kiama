package com.docuai.api.service;

import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.dto.SectionImprovement;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.service.GenerationOrchestrator;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.ai.service.SectionImprovementResponseParser;
import com.docuai.api.dto.DocumentSectionDTO;
import com.docuai.api.dto.SectionSuggestionDTO;
import com.docuai.api.exception.BusinessException;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.DocumentSectionMapper;
import com.docuai.core.model.Document;
import com.docuai.core.model.DocumentSection;
import com.docuai.core.model.DocumentSectionHistory;
import com.docuai.core.model.DocumentSectionHistorySource;
import com.docuai.core.model.DocumentSectionStatut;
import com.docuai.core.repository.DocumentSectionHistoryRepository;
import com.docuai.core.repository.DocumentSectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Édition d'une section de document (Bloc 2 de la refonte) : sauvegarde du
 * contenu écrit par l'utilisateur (autosave), amélioration rédactionnelle par
 * IA (suggestion distincte, jamais appliquée automatiquement), acceptation ou
 * rejet explicite de cette suggestion. {@code rejectSuggestion} n'est pas
 * demandé littéralement par la spec initiale mais nécessaire pour tracer les
 * suggestions acceptées vs rejetées (exigence de logging structuré) — sans
 * lui, un rejet ne serait qu'un choix frontend invisible côté serveur.
 */
@Service
public class DocumentSectionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentSectionService.class);

    private final DocumentSectionRepository documentSectionRepository;
    private final DocumentSectionHistoryRepository documentSectionHistoryRepository;
    private final DocumentSectionMapper documentSectionMapper;
    private final GenerationOrchestrator generationOrchestrator;
    private final PromptBuilder promptBuilder;
    private final SectionImprovementResponseParser sectionImprovementResponseParser;

    public DocumentSectionService(DocumentSectionRepository documentSectionRepository,
                                   DocumentSectionHistoryRepository documentSectionHistoryRepository,
                                   DocumentSectionMapper documentSectionMapper,
                                   GenerationOrchestrator generationOrchestrator,
                                   PromptBuilder promptBuilder,
                                   SectionImprovementResponseParser sectionImprovementResponseParser) {
        this.documentSectionRepository = documentSectionRepository;
        this.documentSectionHistoryRepository = documentSectionHistoryRepository;
        this.documentSectionMapper = documentSectionMapper;
        this.generationOrchestrator = generationOrchestrator;
        this.promptBuilder = promptBuilder;
        this.sectionImprovementResponseParser = sectionImprovementResponseParser;
    }

    @Transactional
    public DocumentSectionDTO saveContent(UUID documentId, UUID sectionId, String content, UUID requestingUserId, boolean isAdmin) {
        DocumentSection section = findOwnedSection(documentId, sectionId, requestingUserId, isAdmin);
        section.setUserContent(content);
        section.setStatut(content == null || content.isBlank() ? DocumentSectionStatut.EMPTY : DocumentSectionStatut.DRAFTED);
        // Le score portait sur la dernière sortie IA : dès que l'utilisateur
        // réécrit la section, il ne décrit plus rien -> effacé plutôt que laissé
        // afficher une confiance sur un texte que le modèle n'a jamais vu.
        section.setConfidenceScore(null);
        section.setDateMaj(LocalDateTime.now());
        documentSectionRepository.save(section);

        if (content != null && !content.isBlank()) {
            documentSectionHistoryRepository.save(DocumentSectionHistory.builder()
                    .documentSection(section).content(content).source(DocumentSectionHistorySource.USER).build());
        }
        return documentSectionMapper.toDto(section);
    }

    /** N'écrase jamais {@code userContent} — la suggestion reste en attente tant que l'utilisateur n'appelle pas {@link #applySuggestion}. */
    @Transactional
    public SectionSuggestionDTO improve(UUID documentId, UUID sectionId, UUID requestingUserId, boolean isAdmin) {
        DocumentSection section = findOwnedSection(documentId, sectionId, requestingUserId, isAdmin);
        if (section.getUserContent() == null || section.getUserContent().isBlank()) {
            throw BusinessException.conflict("SECTION_EMPTY", "Rédigez d'abord un contenu avant de demander une amélioration IA.");
        }

        Document document = section.getDocument();
        String systemPrompt = promptBuilder.buildSectionImprovementSystemPrompt(
                document.getLangue() != null ? document.getLangue().name() : null,
                document.getTon() != null ? document.getTon().name() : null);
        GenerationRequest request = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(section.getUserContent())
                .build();

        SectionImprovement improvement = sectionImprovementResponseParser.parse(
                generationOrchestrator.generate(request).getContent());

        section.setAiSuggestedContent(improvement.getContent());
        section.setConfidenceScore(improvement.getConfidence());
        section.setDateMaj(LocalDateTime.now());
        documentSectionRepository.save(section);

        log.info("suggestion_outcome=GENERATED documentId={} sectionId={} confidence={}",
                documentId, sectionId, improvement.getConfidence());

        SectionSuggestionDTO dto = new SectionSuggestionDTO();
        dto.setAiSuggestedContent(improvement.getContent());
        dto.setConfidenceScore(improvement.getConfidence());
        return dto;
    }

    @Transactional
    public DocumentSectionDTO applySuggestion(UUID documentId, UUID sectionId, UUID requestingUserId, boolean isAdmin) {
        DocumentSection section = findOwnedSection(documentId, sectionId, requestingUserId, isAdmin);
        if (section.getAiSuggestedContent() == null || section.getAiSuggestedContent().isBlank()) {
            throw BusinessException.conflict("NO_SUGGESTION", "Aucune suggestion IA en attente pour cette section.");
        }

        // confidenceScore est conservé tel quel : le texte qu'il qualifiait
        // devient le contenu retenu de la section.
        section.setUserContent(section.getAiSuggestedContent());
        section.setAiSuggestedContent(null);
        section.setStatut(DocumentSectionStatut.AI_IMPROVED);
        section.setDateMaj(LocalDateTime.now());
        documentSectionRepository.save(section);

        documentSectionHistoryRepository.save(DocumentSectionHistory.builder()
                .documentSection(section).content(section.getUserContent()).source(DocumentSectionHistorySource.AI_APPLIED).build());

        log.info("suggestion_outcome=APPLIED documentId={} sectionId={}", documentId, sectionId);
        return documentSectionMapper.toDto(section);
    }

    @Transactional
    public DocumentSectionDTO rejectSuggestion(UUID documentId, UUID sectionId, UUID requestingUserId, boolean isAdmin) {
        DocumentSection section = findOwnedSection(documentId, sectionId, requestingUserId, isAdmin);
        section.setAiSuggestedContent(null);
        // Contrairement à applySuggestion, le score disparaît avec la suggestion
        // rejetée : le contenu retenu reste celui de l'utilisateur, non évalué.
        section.setConfidenceScore(null);
        section.setDateMaj(LocalDateTime.now());
        documentSectionRepository.save(section);

        log.info("suggestion_outcome=REJECTED documentId={} sectionId={}", documentId, sectionId);
        return documentSectionMapper.toDto(section);
    }

    private DocumentSection findOwnedSection(UUID documentId, UUID sectionId, UUID requestingUserId, boolean isAdmin) {
        DocumentSection section = documentSectionRepository.findById(sectionId)
                .orElseThrow(() -> new NotFoundException("SECTION_NOT_FOUND", "Section introuvable."));
        if (!section.getDocument().getId().equals(documentId)) {
            throw new NotFoundException("SECTION_NOT_FOUND", "Section introuvable pour ce document.");
        }
        UUID ownerId = section.getDocument().getUtilisateur() != null ? section.getDocument().getUtilisateur().getId() : null;
        if (!isAdmin && (ownerId == null || !ownerId.equals(requestingUserId))) {
            throw new AccessDeniedException("Cette section ne vous appartient pas.");
        }
        return section;
    }
}
