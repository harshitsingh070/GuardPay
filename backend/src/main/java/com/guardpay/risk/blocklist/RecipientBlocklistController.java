package com.guardpay.risk.blocklist;

import com.guardpay.risk.blocklist.dto.BlocklistEntryDto;
import com.guardpay.risk.blocklist.dto.BlocklistRequestDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo workspace blocklist API. Risk is still decided server-side. */
@RestController
@RequestMapping("/api/blocklist/recipients")
public class RecipientBlocklistController {

  private final RecipientBlocklistService service;

  public RecipientBlocklistController(RecipientBlocklistService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<List<BlocklistEntryDto>> list() {
    return ResponseEntity.ok(service.list());
  }

  @PostMapping
  public ResponseEntity<BlocklistEntryDto> add(
      @Valid @RequestBody BlocklistRequestDto request) {
    return ResponseEntity.ok(service.add(request));
  }

  @DeleteMapping("/{recipientId}")
  public ResponseEntity<Void> remove(@PathVariable UUID recipientId) {
    service.remove(recipientId);
    return ResponseEntity.noContent().build();
  }
}
