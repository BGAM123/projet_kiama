package com.docuai.api.mapper;

import com.docuai.api.dto.DocumentDTO;
import com.docuai.core.model.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** {@code sections}/{@code globalConfidenceScore}/{@code exportUrl} ne sont pas mappés ici (nécessitent une requête séparée / un agrégat sur les sections / une résolution d'URL pré-signée) — renseignés par {@code DocumentService} après appel à {@link #toDto}. */
@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "documentTypeId", source = "documentType.id")
    @Mapping(target = "userId", source = "utilisateur.id")
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
