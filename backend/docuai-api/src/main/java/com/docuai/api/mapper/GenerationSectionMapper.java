package com.docuai.api.mapper;

import com.docuai.api.dto.GenerationSectionDTO;
import com.docuai.core.model.GenerationSectionNode;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GenerationSectionMapper {

    GenerationSectionDTO toDto(GenerationSectionNode node);

    List<GenerationSectionDTO> toDtoList(List<GenerationSectionNode> nodes);

    GenerationSectionNode toEntity(GenerationSectionDTO dto);

    List<GenerationSectionNode> toEntityList(List<GenerationSectionDTO> dtos);
}
