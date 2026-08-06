package com.docuai.api.dto;

import org.springframework.data.domain.Page;

/** Métadonnées de pagination transportées dans {@code ApiResponse.meta} — le contenu de la page reste directement dans {@code data}. */
public record PageMeta(int page, int size, long totalElements, int totalPages) {

    public static PageMeta of(Page<?> page) {
        return new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
