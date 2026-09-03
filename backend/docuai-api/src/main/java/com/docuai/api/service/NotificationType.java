package com.docuai.api.service;

/** Valeurs de {@code notification.type} — doivent rester synchronisées avec le type frontend {@code Notification['type']} (notifications-bell.tsx). */
public enum NotificationType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}
