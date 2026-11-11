package com.guardpay.learning.service;

import com.guardpay.learning.domain.LearningEvent;
import com.guardpay.learning.domain.LearningEventType;
import com.guardpay.learning.repository.LearningEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Writes the append-only learning audit trail. */
@Service
public class LearningAuditService {

  private final LearningEventRepository events;

  public LearningAuditService(LearningEventRepository events) {
    this.events = events;
  }

  public void record(
      UUID actor,
      LearningEventType type,
      UUID datasetId,
      UUID modelId,
      String detail) {
    LearningEvent event = new LearningEvent();
    event.setActorReference(actor);
    event.setEventType(type);
    event.setDatasetId(datasetId);
    event.setModelId(modelId);
    event.setDetail(sanitize(detail));
    event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
    events.save(event);
  }

  static String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String cleaned = value.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
    return cleaned.length() > 1000 ? cleaned.substring(0, 1000) : cleaned;
  }
}
