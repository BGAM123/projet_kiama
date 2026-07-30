package com.docuai.api.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateUserRequest {
    @Email
    private String email;
    private String firstName;
    private String lastName;
    private Boolean active;
    private List<UUID> roleIds;
}
