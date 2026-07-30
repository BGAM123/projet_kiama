package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Enveloppe standard de réponse (section 7 du prompt maître) :
 * { "data": {...}, "error": null, "meta": {...} }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private T data;
    private Object error;
    private Object meta;

    public ApiResponse() {
    }

    private ApiResponse(T data, Object meta) {
        this.data = data;
        this.meta = meta;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> success(T data, Object meta) {
        return new ApiResponse<>(data, meta);
    }

    public static <T> ApiResponse<T> error(Object errorDetails) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setError(errorDetails);
        return response;
    }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public Object getError() { return error; }
    public void setError(Object error) { this.error = error; }

    public Object getMeta() { return meta; }
    public void setMeta(Object meta) { this.meta = meta; }
}
