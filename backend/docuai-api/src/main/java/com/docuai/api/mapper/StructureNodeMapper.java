package com.docuai.api.mapper;

import com.docuai.api.dto.SectionConstraintsDTO;
import com.docuai.api.dto.StructureNodeDTO;
import com.docuai.core.model.SectionConstraints;
import com.docuai.core.model.StructureNode;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapping récursif (children auto-référencé) — MapStruct génère la
 * récursion automatiquement dès lors que {@code toDto}/{@code toEntity}
 * couvrent le type imbriqué (même mapper). {@code constraints} déclaré
 * explicitement (pas de génération implicite silencieuse à espérer sur un
 * type imbriqué non trivial).
 */
@Mapper(componentModel = "spring")
public interface StructureNodeMapper {

    StructureNodeDTO toDto(StructureNode node);

    List<StructureNodeDTO> toDtoList(List<StructureNode> nodes);

    StructureNode toEntity(StructureNodeDTO dto);

    List<StructureNode> toEntityList(List<StructureNodeDTO> dtos);

    SectionConstraintsDTO toDto(SectionConstraints constraints);

    SectionConstraints toEntity(SectionConstraintsDTO dto);
}
