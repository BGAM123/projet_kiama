package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Correspond exactement au type frontend {@code StructureNode}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructureNodeDTO {
    private String id;
    private String type;
    private Integer level;
    private String label;
    private List<StructureNodeDTO> children;
    private List<String> columns;
    private List<TableColumnDefDTO> tableColumns;
    private Integer suggestedRowCount;
    private Boolean required;
    private SectionConstraintsDTO constraints;
}
