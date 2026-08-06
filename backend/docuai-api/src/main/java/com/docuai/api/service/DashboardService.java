package com.docuai.api.service;

import com.docuai.api.dto.CategoryCountDTO;
import com.docuai.api.dto.DailyCountDTO;
import com.docuai.api.dto.DashboardStatsDTO;
import com.docuai.core.model.Categorie;
import com.docuai.core.model.DocumentGenere;
import com.docuai.core.model.DocumentGenereStatut;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.DocumentTypeStatut;
import com.docuai.core.repository.DocumentGenereRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * Statistiques agrégées de l'écran d'accueil (Bloc 8). {@code
 * averageGenerationTimeSec} est une approximation (durée
 * {@code dateCreation -> dateMaj} sur les documents au statut de succès,
 * {@code dateMaj} étant déjà mis à jour à la fin d'une génération SSE par
 * {@link GenerationStreamService}) — pas un chronométrage exact de l'appel
 * IA, faute de colonnes de timing dédiées.
 */
@Service
public class DashboardService {

    private static final Set<DocumentGenereStatut> SUCCESSFUL_STATUSES = Set.of(
            DocumentGenereStatut.GENERE, DocumentGenereStatut.EN_EDITION,
            DocumentGenereStatut.EXPORTE, DocumentGenereStatut.ARCHIVE);

    private final DocumentGenereRepository documentGenereRepository;
    private final DocumentTypeRepository documentTypeRepository;

    public DashboardService(DocumentGenereRepository documentGenereRepository,
                             DocumentTypeRepository documentTypeRepository) {
        this.documentGenereRepository = documentGenereRepository;
        this.documentTypeRepository = documentTypeRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats(UUID userId, UUID requestingUserId, boolean isAdmin) {
        if (!isAdmin && !userId.equals(requestingUserId)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que votre propre tableau de bord.");
        }

        List<DocumentGenere> documents = documentGenereRepository.findByUtilisateurIdWithDocumentTypeAndCategorie(userId);
        LocalDate today = LocalDate.now();

        long documentsThisMonth = documents.stream()
                .filter(d -> !d.getDateCreation().toLocalDate().isBefore(today.withDayOfMonth(1)))
                .count();

        long activeDocumentTypes = documentTypeRepository.findByStatut(DocumentTypeStatut.ACTIF).size();

        long successCount = documents.stream().filter(d -> SUCCESSFUL_STATUSES.contains(d.getStatut())).count();
        int successRate = documents.isEmpty() ? 100 : Math.round(100f * successCount / documents.size());

        OptionalDouble avgSeconds = documents.stream()
                .filter(d -> SUCCESSFUL_STATUSES.contains(d.getStatut()) && d.getDateMaj() != null)
                .mapToLong(d -> Duration.between(d.getDateCreation(), d.getDateMaj()).getSeconds())
                .filter(seconds -> seconds >= 0)
                .average();
        long averageGenerationTimeSec = avgSeconds.isPresent() ? Math.round(avgSeconds.getAsDouble()) : 0L;

        return new DashboardStatsDTO(documentsThisMonth, averageGenerationTimeSec, activeDocumentTypes,
                successRate, byCategory(documents), last7Days(documents, today));
    }

    private List<CategoryCountDTO> byCategory(List<DocumentGenere> documents) {
        Map<String, CategoryCountDTO> counts = new LinkedHashMap<>();
        for (DocumentGenere document : documents) {
            DocumentType documentType = document.getDocumentType();
            Categorie categorie = documentType != null ? documentType.getCategorie() : null;
            String key = categorie != null ? categorie.getId().toString() : "none";
            CategoryCountDTO existing = counts.get(key);
            if (existing == null) {
                String name = categorie != null ? categorie.getNom() : "Autres";
                counts.put(key, new CategoryCountDTO(key, name, 1));
            } else {
                existing.setCount(existing.getCount() + 1);
            }
        }
        return new ArrayList<>(counts.values());
    }

    private List<DailyCountDTO> last7Days(List<DocumentGenere> documents, LocalDate today) {
        List<DailyCountDTO> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long count = documents.stream().filter(d -> d.getDateCreation().toLocalDate().isEqual(day)).count();
            result.add(new DailyCountDTO(day.format(DateTimeFormatter.ISO_LOCAL_DATE), count));
        }
        return result;
    }
}
