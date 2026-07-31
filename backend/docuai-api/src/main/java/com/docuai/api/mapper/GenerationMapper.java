package com.docuai.api.mapper;

import com.docuai.api.dto.GeneratedDocumentDTO;
import com.docuai.core.model.DocumentGenere;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = GenerationSectionMapper.class)
public interface GenerationMapper {

    @Mapping(target = "conversationId", source = "conversation.id")
    @Mapping(target = "documentTypeId", source = "documentType.id")
    @Mapping(target = "userId", source = "utilisateur.id")
    @Mapping(target = "status", source = "statut")
    @Mapping(target = "language", source = "langue")
    @Mapping(target = "tone", source = "ton")
    @Mapping(target = "targetLength", source = "longueurCible")
    @Mapping(target = "contentPivot", source = "promptUtilisateur")
    @Mapping(target = "content", source = "contenu")
    @Mapping(target = "createdAt", source = "dateCreation")
    @Mapping(target = "updatedAt", source = "dateMaj")
    GeneratedDocumentDTO toDto(DocumentGenere documentGenere);

    List<GeneratedDocumentDTO> toDtoList(List<DocumentGenere> documentGeneres);
}
