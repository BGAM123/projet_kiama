package com.docuai.api.mapper;

import com.docuai.api.dto.ReferenceDocumentDTO;
import com.docuai.core.model.DocumentReference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReferenceDocumentMapper {

    @Mapping(target = "conversationId", source = "conversation.id")
    @Mapping(target = "documentId", source = "document.id")
    @Mapping(target = "fileName", source = "nomFichier")
    @Mapping(target = "storagePath", source = "cheminStockage")
    @Mapping(target = "importedAt", source = "dateImport")
    ReferenceDocumentDTO toDto(DocumentReference documentReference);

    List<ReferenceDocumentDTO> toDtoList(List<DocumentReference> documentReferences);
}
