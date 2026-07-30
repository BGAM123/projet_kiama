package com.docuai.api.mapper;

import com.docuai.api.dto.RoleDTO;
import com.docuai.core.model.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = PermissionMapper.class)
public interface RoleMapper {

    @Mapping(target = "name", source = "nom")
    RoleDTO toDto(Role role);

    List<RoleDTO> toDtoList(Set<Role> roles);
}
