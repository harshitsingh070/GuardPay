package com.guardpay.risk.blocklist.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Adds a known recipient to the fraud blocklist. */
public record BlocklistRequestDto(
    @Email(message = "recipientEmail must be a valid email address")
        @Size(max = 255, message = "recipientEmail must be 255 characters or fewer")
        String recipientEmail,
    @Size(max = 255, message = "recipientName must be 255 characters or fewer")
        String recipientName,
    @Size(max = 255, message = "reason must be 255 characters or fewer") String reason) {}
