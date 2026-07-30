package com.docuai.api.mapper;

import com.docuai.api.dto.DocumentTypeDTO;
import com.docuai.core.model.DocumentType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DocumentTypeMapper {

    // Navigation imbriquée nulle-safe générée automatiquement par MapStruct
    // (categorie peut être null — FK nullable en base).
    @Mapping(target = "name", source = "nom")
    @Mapping(target = "categoryId", source = "categorie.id")
    @Mapping(target = "status", source = "statut")
    @Mapping(target = "createdAt", source = "dateCreation")
    DocumentTypeDTO toDto(DocumentType documentType);

    List<DocumentTypeDTO> toDtoList(List<DocumentType> documentTypes);
}
