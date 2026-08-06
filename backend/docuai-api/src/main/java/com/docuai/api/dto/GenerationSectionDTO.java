package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Correspond exactement au type frontend {@code GenerationSection}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerationSectionDTO {
    private String id;
    private String label;
    private String status;
    private String content;
    private String type;
    private Integer level;
    private List<String> columns;
}
