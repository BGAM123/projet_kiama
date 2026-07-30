package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleDTO {
    private UUID id;
    private String name;
    private String description;
    private List<PermissionDTO> permissions;
}
