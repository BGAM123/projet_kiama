package com.docuai.core.service;

import com.docuai.core.model.ActivityLog;
import com.docuai.core.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActivityLogService {

    private final ActivityLogRepository repository;

    public ActivityLogService(ActivityLogRepository repository) {
        this.repository = repository;
    }

    public void logAction(String action, String module, Long userId, String details) {
        ActivityLog log = ActivityLog.builder()
                .action(action)
                .module(module)
                .userId(userId)
                .details(details)
                .build();
        repository.save(log);
    }

    public List<ActivityLog> getRecentLogs() {
        return repository.findTop10ByOrderByTimestampDesc();
    }
}
