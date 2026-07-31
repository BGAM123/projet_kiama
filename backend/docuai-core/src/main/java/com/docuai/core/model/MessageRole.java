package com.docuai.core.model;

/** Rôle d'un message de conversation (contrainte {@code chk_message_role} de V1__init_schema.sql). */
public enum MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
