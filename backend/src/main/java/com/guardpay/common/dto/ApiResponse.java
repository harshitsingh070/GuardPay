package com.guardpay.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard envelope for GuardPay REST responses.
 *
 * <p>Convention for all future business endpoints (layering rule):
 * controllers return {@code ApiResponse} DTOs — never JPA entities.
 *
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String status, String message, T data) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>("OK", null, data);
  }

  public static <T> ApiResponse<T> ok(String message, T data) {
    return new ApiResponse<>("OK", message, data);
  }

  public static <T> ApiResponse<T> error(String message) {
    return new ApiResponse<>("ERROR", message, null);
  }
}
