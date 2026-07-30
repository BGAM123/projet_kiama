package com.docuai.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Point d'entrée Spring Boot.
 *
 * scanBasePackages="com.docuai" : les composants (contrôleurs, services,
 * repositories, entités JPA) sont répartis sur plusieurs modules Maven dont
 * les packages ne descendent pas tous de com.docuai.api (com.docuai.core,
 * com.docuai.security, com.docuai.extraction, com.docuai.ai, com.docuai.export).
 *
 * @EntityScan / @EnableJpaRepositories : le scan automatique des entités JPA
 * et repositories Spring Data ne suit PAS scanBasePackages, il se base
 * uniquement sur le package de cette classe (com.docuai.api) — les
 * entités/repositories vivront dans com.docuai.core (Bloc 2 et suivants).
 *
 * Bloc 1 (socle) : cette classe ne fait encore rien de métier — elle permet
 * seulement de vérifier que le multi-module compile et démarre contre la
 * base migrée par Flyway (V1/V2), avant d'ajouter la sécurité au Bloc 2.
 */
@SpringBootApplication(scanBasePackages = "com.docuai")
@EntityScan("com.docuai.core.model")
@EnableJpaRepositories("com.docuai.core.repository")
public class DocuAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocuAiApplication.class, args);
    }
}
