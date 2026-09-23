package com.coyotai.education.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Uniform envelope for every REST response: {"success":..,"message":..,"data":..}. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> errors
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static <T> ApiResponse<T> error(String message, Map<String, String> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }
}
