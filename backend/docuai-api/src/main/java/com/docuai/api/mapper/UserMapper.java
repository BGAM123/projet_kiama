package com.docuai.api.mapper;

import com.docuai.api.dto.UserDTO;
import com.docuai.core.model.Utilisateur;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = RoleMapper.class)
public interface UserMapper {

    @Mapping(target = "firstName", source = "prenom")
    @Mapping(target = "lastName", source = "nom")
    @Mapping(target = "active", source = "actif")
    @Mapping(target = "createdAt", source = "dateCreation")
    UserDTO toDto(Utilisateur utilisateur);
}
