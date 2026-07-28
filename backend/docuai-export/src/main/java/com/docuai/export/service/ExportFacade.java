package com.docuai.export.service;

import com.docuai.export.dto.ExportRequest;
import com.docuai.export.strategy.DocumentGeneratorStrategy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExportFacade {

    private final Map<com.docuai.export.enums.ExportFormat, DocumentGeneratorStrategy> strategies;

    public ExportFacade(List<DocumentGeneratorStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(DocumentGeneratorStrategy::getSupportedFormat, s -> s));
    }

    public DocumentGeneratorStrategy getStrategy(com.docuai.export.enums.ExportFormat format) {
        DocumentGeneratorStrategy strategy = strategies.get(format);
        if (strategy == null) {
            throw new IllegalArgumentException("Format d'exportation non supporté : " + format);
        }
        return strategy;
    }
    
    public byte[] exportDocument(ExportRequest request) {
        if (request.getFormat() == null) {
            throw new IllegalArgumentException("Le format d'exportation doit être défini.");
        }
        return getStrategy(request.getFormat()).generateDocument(request);
    }
}
