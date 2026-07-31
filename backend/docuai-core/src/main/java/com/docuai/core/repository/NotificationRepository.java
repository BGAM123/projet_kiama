package com.docuai.core.repository;

import com.docuai.core.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUtilisateur_IdOrderByDateCreationDesc(UUID userId);
}
