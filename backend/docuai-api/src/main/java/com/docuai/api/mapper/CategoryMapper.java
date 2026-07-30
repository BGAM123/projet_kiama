package com.docuai.api.mapper;

import com.docuai.api.dto.CategoryDTO;
import com.docuai.core.model.Categorie;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "name", source = "nom")
    CategoryDTO toDto(Categorie categorie);

    List<CategoryDTO> toDtoList(List<Categorie> categories);
}
