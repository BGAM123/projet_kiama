package com.docuai.export;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private final Map<ExportFormat, DocumentExporter> exportersByFormat;

    public ExportService(List<DocumentExporter> exporters) {
        this.exportersByFormat = exporters.stream()
                .collect(Collectors.toMap(DocumentExporter::supportedFormat, Function.identity()));
    }

    public ExportedFile export(ExportContent request, ExportFormat format) {
        DocumentExporter exporter = exportersByFormat.get(format);
        if (exporter == null) {
            throw new IllegalArgumentException("Format d'export non supporté : " + format);
        }
        return exporter.export(request);
    }
}
