package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

/** Correspond exactement au type frontend {@code BackendNotification} (frontend/lib/api/client.ts). */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDTO {
    private UUID id;
    private UUID userId;
    private String type;
    private String contenu;
    private Boolean lue;
    private String dateCreation;
}
