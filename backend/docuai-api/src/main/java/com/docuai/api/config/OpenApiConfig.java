package com.docuai.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Déclare le schéma d'authentification "bearerAuth" pour Swagger UI (bouton
 * "Authorize" avec un jeton JWT), conformément à la section 12 du prompt
 * maître ("Documentation API : Swagger UI complet... exemples de requêtes").
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "DocuAI API",
                version = "v1",
                description = "Extraction de structure documentaire + génération IA multi-fournisseurs. "
                        + "Authentification : POST /api/v1/auth/login puis bouton \"Authorize\" avec le accessToken reçu."
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
