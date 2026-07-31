package com.docuai.api.dto;

import lombok.Data;

/** Correspond exactement au type frontend {@code ExtractedContentDetail} (frontend/lib/api/client.ts). */
@Data
public class ExtractedContentDTO {
    private String fileName;
    private String mimeType;
    private String rawText;
    private String fileUrl;
}
