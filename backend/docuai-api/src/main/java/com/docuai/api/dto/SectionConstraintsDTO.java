package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** Correspond exactement au type frontend {@code SectionConstraints}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectionConstraintsDTO {
    private Integer minLength;
    private Integer maxLength;
    private String format;
    private String pattern;
}
