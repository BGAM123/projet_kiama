package com.docuai.api.mapper;

import com.docuai.api.dto.NotificationDTO;
import com.docuai.core.model.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    // Navigation imbriquée nulle-safe générée automatiquement par MapStruct
    // (utilisateur peut être null — FK nullable en base, cf. V1__init_schema.sql).
    @Mapping(target = "userId", source = "utilisateur.id")
    NotificationDTO toDto(Notification notification);

    List<NotificationDTO> toDtoList(List<Notification> notifications);
}
