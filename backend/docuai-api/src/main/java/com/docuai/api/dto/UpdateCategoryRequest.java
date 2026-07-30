package com.docuai.api.dto;

import lombok.Data;

@Data
public class UpdateCategoryRequest {
    private String name;
    private String description;
}
