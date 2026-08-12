package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** Correspond exactement au type frontend {@code TableColumnDef}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TableColumnDefDTO {
    private String name;
    private String type;
}
