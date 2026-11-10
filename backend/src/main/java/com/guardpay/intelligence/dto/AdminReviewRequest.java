package com.guardpay.intelligence.dto;

import com.guardpay.intelligence.domain.ReviewAction;
import jakarta.validation.constraints.NotNull;

/** Admin review command. */
public record AdminReviewRequest(@NotNull ReviewAction action, String note) {}
