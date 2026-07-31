package com.docuai.core.repository;

import com.docuai.core.model.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, UUID> {

    Optional<AiModelConfig> findByEstDefautTrueAndActifTrue();

    List<AiModelConfig> findByFournisseurAndActifTrue(String fournisseur);

    List<AiModelConfig> findByActifTrue();
}
