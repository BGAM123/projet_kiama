package com.docuai.api.config;

import com.docuai.api.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Enveloppe systématiquement les réponses JSON des contrôleurs REST dans
 * ApiResponse&lt;T&gt; ({ data, error, meta }), conformément à la section 7
 * du prompt maître.
 *
 * Ne s'applique PAS (décidé dans beforeBodyWrite, pas dans supports(), pour
 * pouvoir se baser sur le content-type réellement sélectionné) :
 *  - aux réponses déjà enveloppées manuellement (ex. AuthController) ;
 *  - aux flux binaires (export DOCX/PDF, type Resource/byte[]) — Bloc 7 ;
 *  - aux flux SSE (SseEmitter) — Bloc 6 ;
 *  - à tout ce qui n'est pas explicitement du JSON.
 */
@ControllerAdvice(basePackages = "com.docuai.api.controller")
public class ApiResponseWrapperAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> type = returnType.getParameterType();
        return !SseEmitter.class.isAssignableFrom(type)
                && !Resource.class.isAssignableFrom(type)
                && !byte[].class.isAssignableFrom(type);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse) {
            return body;
        }
        if (selectedContentType != null && !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }
        return ApiResponse.success(body);
    }
}
