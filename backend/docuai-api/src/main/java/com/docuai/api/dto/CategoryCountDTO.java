package com.docuai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CategoryCountDTO {
    private String categoryId;
    private String categoryName;
    private long count;
}
