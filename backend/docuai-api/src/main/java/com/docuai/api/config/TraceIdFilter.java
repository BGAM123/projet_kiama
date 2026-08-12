package com.docuai.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Génère un identifiant de corrélation par requête (repris de l'en-tête
 * {@code X-Trace-Id} s'il est fourni par l'appelant, sinon généré) et le
 * place en MDC sous la clé {@code traceId} pour toute la durée de la
 * requête — repris automatiquement dans chaque ligne de log JSON
 * (logback-spring.xml, {@code LogstashEncoder}) émise pendant son
 * traitement, y compris pour tracer les suggestions IA acceptées/rejetées
 * (DocumentSectionService). Retourné en en-tête de réponse pour permettre au
 * frontend de le corréler à ses propres logs.
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
