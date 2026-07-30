package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private boolean active;
    private String createdAt;
    private List<RoleDTO> roles;
}
