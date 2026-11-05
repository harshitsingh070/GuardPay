package com.guardpay.risk.blocklist;

import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.blocklist.dto.BlocklistEntryDto;
import com.guardpay.risk.blocklist.dto.BlocklistRequestDto;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owner-scoped management of reported counterparties. */
@Service
public class RecipientBlocklistService {

  private final RecipientBlocklistRepository blocklist;
  private final RecipientRepository recipients;
  private final DemoUserService demoUser;

  public RecipientBlocklistService(
      RecipientBlocklistRepository blocklist,
      RecipientRepository recipients,
      DemoUserService demoUser) {
    this.blocklist = blocklist;
    this.recipients = recipients;
    this.demoUser = demoUser;
  }

  @Transactional(readOnly = true)
  public List<BlocklistEntryDto> list() {
    return blocklist.findByUserIdOrderByCreatedAtDesc(demoUser.currentUser().getId()).stream()
        .map(RecipientBlocklistService::toDto)
        .toList();
  }

  @Transactional
  public BlocklistEntryDto add(BlocklistRequestDto request) {
    User user = demoUser.currentUser();
    Recipient recipient = resolveRecipient(user, request);
    RecipientBlocklistEntry entry =
        blocklist
            .findByUserIdAndRecipientId(user.getId(), recipient.getId())
            .orElseGet(
                () -> {
                  RecipientBlocklistEntry fresh = new RecipientBlocklistEntry();
                  fresh.setUser(user);
                  fresh.setRecipient(recipient);
                  return fresh;
                });
    entry.setReason(blankToNull(request.reason()));
    return toDto(blocklist.save(entry));
  }

  @Transactional
  public void remove(UUID recipientId) {
    User user = demoUser.currentUser();
    blocklist
        .findByUserIdAndRecipientId(user.getId(), recipientId)
        .ifPresent(blocklist::delete);
  }

  private Recipient resolveRecipient(User user, BlocklistRequestDto request) {
    String email = blankToNull(request.recipientEmail());
    String name = blankToNull(request.recipientName());
    if (email == null && name == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "recipientEmail or recipientName is required");
    }
    return (email != null
            ? recipients.findByUserIdAndEmailIgnoreCase(user.getId(), email)
            : recipients.findByUserIdAndNameIgnoreCase(user.getId(), name))
        .or(() -> name != null
            ? recipients.findByUserIdAndNameIgnoreCase(user.getId(), name)
            : java.util.Optional.empty())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No known recipient matches; make or review a payment to this recipient first"));
  }

  private static BlocklistEntryDto toDto(RecipientBlocklistEntry entry) {
    Recipient recipient = entry.getRecipient();
    return new BlocklistEntryDto(
        entry.getId(),
        recipient.getId(),
        recipient.getName(),
        recipient.getEmail(),
        entry.getReason(),
        entry.getCreatedAt());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
