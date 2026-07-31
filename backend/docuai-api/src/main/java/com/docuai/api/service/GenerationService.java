package com.docuai.api.service;

import com.docuai.api.dto.GeneratedDocumentDTO;
import com.docuai.api.dto.StartGenerationRequest;
import com.docuai.api.dto.UpdateGenerationRequest;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.GenerationMapper;
import com.docuai.core.model.Conversation;
import com.docuai.core.model.DocumentGenere;
import com.docuai.core.model.DocumentGenereStatut;
import com.docuai.core.model.DocumentStructure;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.GenerationSectionNode;
import com.docuai.core.model.StructureNode;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.DocumentGenereRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import com.docuai.core.repository.UtilisateurRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service applicatif : cycle de vie d'un document généré (création, lecture,
 * édition manuelle). Le déroulement effectif de la génération section par
 * section (appels IA, SSE) relève de {@link GenerationStreamService}.
 */
@Service
public class GenerationService {

    private final DocumentGenereRepository documentGenereRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentStructureRepository documentStructureRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ConversationService conversationService;
    private final GenerationMapper generationMapper;

    public GenerationService(DocumentGenereRepository documentGenereRepository,
                              DocumentTypeRepository documentTypeRepository,
                              DocumentStructureRepository documentStructureRepository,
                              UtilisateurRepository utilisateurRepository,
                              ConversationService conversationService,
                              GenerationMapper generationMapper) {
        this.documentGenereRepository = documentGenereRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.documentStructureRepository = documentStructureRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.conversationService = conversationService;
        this.generationMapper = generationMapper;
    }

    /**
     * Crée le document généré et initialise ses sections (une par nœud de la
     * structure attendue, hors "cover") avec le statut {@code PENDING} —
     * reproduit exactement le contrat déjà stabilisé côté frontend
     * ({@code startGeneration}, frontend/lib/api/client.ts) : le document part
     * directement au statut {@code EN_GENERATION}, le déroulement réel des
     * sections se fait via {@code GET /generations/{id}/stream}.
     */
    @Transactional
    public GeneratedDocumentDTO start(StartGenerationRequest request, UUID requestingUserId, boolean isAdmin) {
        Conversation conversation = conversationService.findConversation(request.getConversationId());
        conversationService.checkOwnership(conversation, requestingUserId, isAdmin);

        Utilisateur utilisateur = utilisateurRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Utilisateur introuvable."));
        DocumentType documentType = documentTypeRepository.findById(request.getDocumentTypeId())
                .orElseThrow(() -> new NotFoundException("DOCUMENT_TYPE_NOT_FOUND", "Document Type introuvable."));

        DocumentGenere document = DocumentGenere.builder()
                .conversation(conversation)
                .documentType(documentType)
                .utilisateur(utilisateur)
                .statut(DocumentGenereStatut.EN_GENERATION)
                .langue(request.getLanguage())
                .ton(request.getTone())
                .longueurCible(request.getTargetLength())
                .promptUtilisateur(request.getContentPivot())
                .contenu("")
                .sections(buildInitialSections(documentType.getId()))
                .build();

        return generationMapper.toDto(documentGenereRepository.save(document));
    }

    private List<GenerationSectionNode> buildInitialSections(UUID documentTypeId) {
        List<GenerationSectionNode> sections = new ArrayList<>();
        documentStructureRepository.findByDocumentType_Id(documentTypeId).map(DocumentStructure::getArbreJson)
                .ifPresent(tree -> {
                    for (StructureNode node : tree) {
                        if ("cover".equals(node.getType())) {
                            continue;
                        }
                        sections.add(GenerationSectionNode.builder()
                                .id(UUID.randomUUID().toString())
                                .label(node.getLabel())
                                .status("PENDING")
                                .content("")
                                .build());
                    }
                });
        return sections;
    }

    @Transactional(readOnly = true)
    public GeneratedDocumentDTO getById(UUID id, UUID requestingUserId, boolean isAdmin) {
        DocumentGenere document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);
        return generationMapper.toDto(document);
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentDTO> listForUser(UUID userId, UUID requestingUserId, boolean isAdmin) {
        if (!isAdmin && !userId.equals(requestingUserId)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que votre propre historique.");
        }
        return generationMapper.toDtoList(documentGenereRepository.findByUtilisateur_IdOrderByDateCreationDesc(userId));
    }

    @Transactional
    public GeneratedDocumentDTO update(UUID id, UpdateGenerationRequest request, UUID requestingUserId, boolean isAdmin) {
        DocumentGenere document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);
        if (request.getContent() != null) {
            document.setContenu(request.getContent());
        }
        document.setStatut(DocumentGenereStatut.EN_EDITION);
        document.setDateMaj(LocalDateTime.now());
        return generationMapper.toDto(documentGenereRepository.save(document));
    }

    DocumentGenere findEntity(UUID id) {
        return documentGenereRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("GENERATION_NOT_FOUND", "Génération introuvable."));
    }

    void checkOwnership(DocumentGenere document, UUID requestingUserId, boolean isAdmin) {
        UUID ownerId = document.getUtilisateur() != null ? document.getUtilisateur().getId() : null;
        if (!isAdmin && (ownerId == null || !ownerId.equals(requestingUserId))) {
            throw new AccessDeniedException("Ce document généré ne vous appartient pas.");
        }
    }
}
