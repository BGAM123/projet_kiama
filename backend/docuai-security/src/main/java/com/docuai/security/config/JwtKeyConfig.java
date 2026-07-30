package com.docuai.security.config;

import com.docuai.security.jwt.PemKeyReader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Décode une seule fois au démarrage les clés RSA (RS256) déclarées en PEM
 * dans la configuration, et les expose comme beans réutilisables par
 * JwtTokenProvider. Échoue au démarrage (fail-fast) si les clés sont absentes
 * ou illisibles plutôt qu'à la première requête JWT.
 */
@Configuration
@EnableConfigurationProperties(JwtKeyProperties.class)
public class JwtKeyConfig {

    @Bean
    public RSAPrivateKey jwtPrivateKey(JwtKeyProperties properties) {
        return PemKeyReader.readPrivateKey(properties.getPrivateKey());
    }

    @Bean
    public RSAPublicKey jwtPublicKey(JwtKeyProperties properties) {
        return PemKeyReader.readPublicKey(properties.getPublicKey());
    }
}
