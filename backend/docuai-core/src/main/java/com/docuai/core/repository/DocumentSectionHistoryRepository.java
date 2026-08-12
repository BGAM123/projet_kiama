package com.docuai.core.repository;

import com.docuai.core.model.DocumentSectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentSectionHistoryRepository extends JpaRepository<DocumentSectionHistory, UUID> {
}
