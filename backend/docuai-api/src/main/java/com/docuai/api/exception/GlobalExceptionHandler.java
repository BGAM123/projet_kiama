package com.docuai.api.exception;

import com.docuai.api.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception ex, HttpServletRequest request) {
        return buildErrorResponse("INTERNAL_SERVER_ERROR", "Une erreur technique inattendue s'est produite.", request.getRequestURI(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildErrorResponse("FORBIDDEN", "Vous n'avez pas les permissions nécessaires pour cette action.", request.getRequestURI(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        return buildErrorResponse("UNAUTHORIZED", "Identifiants invalides ou session expirée.", request.getRequestURI(), HttpStatus.UNAUTHORIZED);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return buildErrorResponse("BAD_REQUEST", "Les données fournies sont invalides.", request.getRequestURI(), HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<ApiResponse<Object>> buildErrorResponse(String code, String message, String path, HttpStatus status) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("code", code);
        errorDetails.put("message", message);
        errorDetails.put("timestamp", Instant.now().toString());
        errorDetails.put("path", path);
        return ResponseEntity.status(status).body(ApiResponse.error(errorDetails));
    }
}
