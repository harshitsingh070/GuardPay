package com.guardpay.transaction.dto;

import java.util.List;

/** One page of history plus the paging metadata the UI needs. */
public record TransactionPageDto(
    List<TransactionSummaryDto> items, int page, int size, long totalItems, int totalPages) {}
