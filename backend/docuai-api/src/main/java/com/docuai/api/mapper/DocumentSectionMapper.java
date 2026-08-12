package com.docuai.api.mapper;

import com.docuai.api.dto.DocumentSectionDTO;
import com.docuai.api.dto.TableColumnDefDTO;
import com.docuai.core.model.DocumentSection;
import com.docuai.core.model.TableColumnDef;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DocumentSectionMapper {

    @Mapping(target = "parentSectionId", source = "parentSection.id")
    @Mapping(target = "order", source = "orderIndex")
    @Mapping(target = "status", source = "statut")
    DocumentSectionDTO toDto(DocumentSection section);

    List<DocumentSectionDTO> toDtoList(List<DocumentSection> sections);

    TableColumnDefDTO toDto(TableColumnDef column);

    TableColumnDef toEntity(TableColumnDefDTO dto);
}
