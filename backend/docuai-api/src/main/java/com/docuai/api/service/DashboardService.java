package com.docuai.api.service;

import com.docuai.api.dto.CategoryCountDTO;
import com.docuai.api.dto.DailyCountDTO;
import com.docuai.api.dto.DashboardStatsDTO;
import com.docuai.core.model.Categorie;
import com.docuai.core.model.Document;
import com.docuai.core.model.DocumentStatut;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.DocumentTypeStatut;
import com.docuai.core.repository.DocumentRepository;
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
import java.util.UUID;

/**
 * Statistiques agrégées de l'écran d'accueil (Bloc 8). Depuis le passage à
 * l'édition manuelle assistée (plus de génération/échec IA au niveau du
 * document), {@code successRate} et {@code averageGenerationTimeSec}
 * changent de sens sans changer de nom (contrat frontend inchangé,
 * DashboardStatsDTO) : {@code successRate} devient le taux de
 * finalisation (documents {@code FINALISE} parmi tous les documents créés) ;
 * {@code averageGenerationTimeSec} devient le temps moyen de rédaction
 * ({@code dateCreation -> dateMaj} sur les documents finalisés).
 */
@Service
public class DashboardService {

    private final DocumentRepository documentRepository;
    private final DocumentTypeRepository documentTypeRepository;

    public DashboardService(DocumentRepository documentRepository,
                             DocumentTypeRepository documentTypeRepository) {
        this.documentRepository = documentRepository;
        this.documentTypeRepository = documentTypeRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats(UUID userId, UUID requestingUserId, boolean isAdmin) {
        if (!isAdmin && !userId.equals(requestingUserId)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que votre propre tableau de bord.");
        }

        List<Document> documents = documentRepository.findByUtilisateurIdWithDocumentTypeAndCategorie(userId);
        LocalDate today = LocalDate.now();

        long documentsThisMonth = documents.stream()
                .filter(d -> !d.getDateCreation().toLocalDate().isBefore(today.withDayOfMonth(1)))
                .count();

        long activeDocumentTypes = documentTypeRepository.findByStatut(DocumentTypeStatut.ACTIF).size();

        long finalizedCount = documents.stream().filter(d -> d.getStatut() == DocumentStatut.FINALISE).count();
        int successRate = documents.isEmpty() ? 100 : Math.round(100f * finalizedCount / documents.size());

        OptionalDouble avgSeconds = documents.stream()
                .filter(d -> d.getStatut() == DocumentStatut.FINALISE && d.getDateMaj() != null)
                .mapToLong(d -> Duration.between(d.getDateCreation(), d.getDateMaj()).getSeconds())
                .filter(seconds -> seconds >= 0)
                .average();
        long averageGenerationTimeSec = avgSeconds.isPresent() ? Math.round(avgSeconds.getAsDouble()) : 0L;

        return new DashboardStatsDTO(documentsThisMonth, averageGenerationTimeSec, activeDocumentTypes,
                successRate, byCategory(documents), last7Days(documents, today));
    }

    private List<CategoryCountDTO> byCategory(List<Document> documents) {
        Map<String, CategoryCountDTO> counts = new LinkedHashMap<>();
        for (Document document : documents) {
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

    private List<DailyCountDTO> last7Days(List<Document> documents, LocalDate today) {
        List<DailyCountDTO> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long count = documents.stream().filter(d -> d.getDateCreation().toLocalDate().isEqual(day)).count();
            result.add(new DailyCountDTO(day.format(DateTimeFormatter.ISO_LOCAL_DATE), count));
        }
        return result;
    }
}
