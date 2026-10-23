package com.guardpay.transaction;

import com.guardpay.transaction.dto.TransactionDetailDto;
import com.guardpay.transaction.dto.TransactionPageDto;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only transaction history for the fixed demo account.
 *
 * <p>Clients cannot select or widen the history scope.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

  private final TransactionQueryService service;

  public TransactionController(TransactionQueryService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<TransactionPageDto> list(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return ResponseEntity.ok(service.list(page, size));
  }

  @GetMapping("/{id}")
  public ResponseEntity<TransactionDetailDto> detail(@PathVariable UUID id) {
    return ResponseEntity.ok(service.detail(id));
  }
}
