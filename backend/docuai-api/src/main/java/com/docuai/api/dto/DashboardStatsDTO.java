package com.docuai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DashboardStatsDTO {
    private long documentsThisMonth;
    private long averageGenerationTimeSec;
    private long activeDocumentTypes;
    private int successRate;
    private List<CategoryCountDTO> byCategory;
    private List<DailyCountDTO> last7Days;
}
