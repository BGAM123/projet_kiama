package com.docuai.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ce point d'entrée court-circuite le @ControllerAdvice global
 * (GlobalExceptionHandler) pour toute requête non authentifiée sur une route
 * protégée : il doit donc produire exactement le même format d'erreur
 * ({ error: { code, message, timestamp, path } }, section 7 du prompt
 * maître), sinon le client reçoit deux formes d'erreur différentes selon
 * l'origine du 401.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                          HttpServletResponse response,
                          AuthenticationException authException) throws IOException, ServletException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Map<String, Object> errorDetails = new LinkedHashMap<>();
        errorDetails.put("code", "UNAUTHORIZED");
        errorDetails.put("message", "Accès non autorisé.");
        errorDetails.put("timestamp", Instant.now().toString());
        errorDetails.put("path", request.getRequestURI());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", errorDetails);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
