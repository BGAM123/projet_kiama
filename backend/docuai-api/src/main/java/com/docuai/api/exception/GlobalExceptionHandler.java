package com.docuai.api.exception;

import com.docuai.api.dto.ApiResponse;
import com.docuai.extraction.storage.ObjectStorageException;
import com.docuai.extraction.text.TextExtractionException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestion centralisée des erreurs (section 7 et 9 du prompt maître) : format
 * homogène { "error": { code, message, timestamp, path } } sur tous les cas
 * couverts (validation 400, auth 401, autorisation 403, introuvable 404,
 * conflit métier 409, technique 500, service externe indisponible 503 via
 * BusinessException).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(ex.getCode(), ex.getMessage(), request.getRequestURI(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return buildErrorResponse(ex.getCode(), ex.getMessage(), request.getRequestURI(), ex.getStatus());
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
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        Map<String, Object> errorDetails = baseErrorDetails("VALIDATION_ERROR", "Les données fournies sont invalides.", request.getRequestURI());
        errorDetails.put("fields", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(errorDetails));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return buildErrorResponse("BAD_REQUEST", ex.getMessage(), request.getRequestURI(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return buildErrorResponse("FILE_TOO_LARGE", "Le fichier dépasse la taille maximale autorisée (25 Mo).", request.getRequestURI(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TextExtractionException.class)
    public ResponseEntity<ApiResponse<Object>> handleTextExtraction(TextExtractionException ex, HttpServletRequest request) {
        log.warn("Échec d'extraction de texte sur {} : {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildErrorResponse("EXTRACTION_FAILED", ex.getMessage(), request.getRequestURI(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ObjectStorageException.class)
    public ResponseEntity<ApiResponse<Object>> handleObjectStorage(ObjectStorageException ex, HttpServletRequest request) {
        log.error("Échec de stockage objet (MinIO) sur {} : {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildErrorResponse("STORAGE_UNAVAILABLE", ex.getMessage(), request.getRequestURI(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception ex, HttpServletRequest request) {
        // Le corps de réponse reste volontairement générique (pas de fuite de
        // détail d'implémentation côté client) — la trace complète part dans
        // les logs serveur (`docker compose logs backend`), seul endroit où
        // diagnostiquer un 500 inattendu.
        log.error("Erreur technique inattendue sur {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildErrorResponse("INTERNAL_SERVER_ERROR", "Une erreur technique inattendue s'est produite.", request.getRequestURI(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Object>> buildErrorResponse(String code, String message, String path, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.error(baseErrorDetails(code, message, path)));
    }

    private Map<String, Object> baseErrorDetails(String code, String message, String path) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("code", code);
        errorDetails.put("message", message);
        errorDetails.put("timestamp", Instant.now().toString());
        errorDetails.put("path", path);
        return errorDetails;
    }
}
