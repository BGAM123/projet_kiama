package com.docuai.api.mapper;

import com.docuai.api.dto.PermissionDTO;
import com.docuai.core.model.Permission;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    PermissionDTO toDto(Permission permission);
}
