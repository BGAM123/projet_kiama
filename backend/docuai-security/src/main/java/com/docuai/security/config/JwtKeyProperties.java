package com.docuai.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Liaison de docuai.jwt.* (application.yml / variables d'environnement).
 * @ConfigurationProperties plutôt que plusieurs @Value épars, pour centraliser
 * la validation et l'accès (issuer, TTL, PEM bruts avant décodage par
 * PemKeyReader dans JwtKeyConfig).
 *
 * Pas de @Component ici : l'enregistrement comme bean est délégué à
 * @EnableConfigurationProperties(JwtKeyProperties.class) sur JwtKeyConfig,
 * pour éviter un double enregistrement (scan de composants + registrar de
 * @EnableConfigurationProperties) et garder cette classe confinée à son
 * point d'usage plutôt qu'ambiante dans tout le contexte Spring.
 */
@ConfigurationProperties(prefix = "docuai.jwt")
@Getter
@Setter
public class JwtKeyProperties {
    private String issuer;
    private String privateKey;
    private String publicKey;
    private long accessTtlSeconds;
    private long refreshTtlSeconds;
}
