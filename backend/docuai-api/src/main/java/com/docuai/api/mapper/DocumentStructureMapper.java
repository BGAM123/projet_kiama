package com.docuai.api.mapper;

import com.docuai.api.dto.DocumentStructureDTO;
import com.docuai.core.model.DocumentStructure;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = StructureNodeMapper.class)
public interface DocumentStructureMapper {

    @Mapping(target = "documentTypeId", source = "documentType.id")
    @Mapping(target = "tree", source = "arbreJson")
    @Mapping(target = "hasToc", source = "possedeToc")
    DocumentStructureDTO toDto(DocumentStructure structure);
}
