package com.docuai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DailyCountDTO {
    private String date;
    private long count;
}
