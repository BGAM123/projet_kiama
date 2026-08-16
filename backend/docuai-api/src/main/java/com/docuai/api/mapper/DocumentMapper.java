package com.docuai.api.mapper;

import com.docuai.api.dto.DocumentDTO;
import com.docuai.core.model.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * {@code sections}/{@code globalConfidenceScore}/{@code exportUrl} ne sont pas
 * mappés ici (nécessitent une requête séparée / un agrégat sur les sections /
 * une résolution d'URL pré-signée) — renseignés par {@code DocumentService}
 * après appel à {@link #toDto}. {@code title} de même : sa résolution dépend
 * à la fois de {@code Document.titre} et de {@code Document.documentType},
 * deux champs alternatifs qu'un mapping déclaratif simple ne peut pas
 * arbitrer.
 */
@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "documentTypeId", source = "documentType.id")
    @Mapping(target = "userId", source = "utilisateur.id")
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "status", source = "statut")
    @Mapping(target = "language", source = "langue")
    @Mapping(target = "tone", source = "ton")
    @Mapping(target = "createdAt", source = "dateCreation")
    @Mapping(target = "updatedAt", source = "dateMaj")
    @Mapping(target = "sections", ignore = true)
    @Mapping(target = "globalConfidenceScore", ignore = true)
    @Mapping(target = "exportUrl", ignore = true)
    DocumentDTO toDto(Document document);

    List<DocumentDTO> toDtoList(List<Document> documents);
}
