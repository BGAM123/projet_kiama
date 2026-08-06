package com.docuai.ai.enums;

/**
 * Fournisseurs IA supportés — valeurs alignées avec la contrainte
 * {@code chk_ai_fournisseur} de V1__init_schema.sql. {@code CLAUDE} désigne
 * Anthropic (nom retenu côté schéma/frontend), pas {@code ANTHROPIC}.
 */
public enum AiProvider {
    OPENAI,
    CLAUDE,
    GEMINI,
    MISTRAL,
    OLLAMA,
    DEEPSEEK,
    GROQ,
    QWEN
}
