package com.docuai.api.mapper;

import com.docuai.api.dto.ConversationDTO;
import com.docuai.core.model.Conversation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ConversationMapper {

    @Mapping(target = "userId", source = "utilisateur.id")
    @Mapping(target = "documentTypeId", source = "documentType.id")
    @Mapping(target = "title", source = "titre")
    @Mapping(target = "createdAt", source = "dateCreation")
    ConversationDTO toDto(Conversation conversation);

    List<ConversationDTO> toDtoList(List<Conversation> conversations);
}
