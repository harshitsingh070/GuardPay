package com.guardpay.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Answer supplied by the user to complete a controlled VERIFY challenge. */
public record VerificationConfirmRequestDto(
    @NotBlank(message = "verification code is required")
        @Size(max = 16, message = "verification code is too long")
        String code) {}
