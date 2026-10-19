package com.guardpay.common.dto;

import java.time.Instant;

/**
 * Standard error payload returned by {@code GlobalExceptionHandler}.
 */
public record ApiError(int status, String error, String message, String path, Instant timestamp) {}
