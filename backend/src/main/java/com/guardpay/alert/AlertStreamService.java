package com.guardpay.alert;

import com.guardpay.alert.dto.AlertDto;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-process, owner-scoped Server-Sent Events fan-out. Each connection only
 * receives alerts for the authenticated account that created it.
 */
@Service
public class AlertStreamService {

  private static final Logger log = LoggerFactory.getLogger(AlertStreamService.class);
  private static final long NO_TIMEOUT = 0L;
  private static final UUID ANONYMOUS = new UUID(0L, 0L);

  private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
      new ConcurrentHashMap<>();

  public SseEmitter subscribe(UUID userId, List<AlertDto> snapshot) {
    UUID owner = owner(userId);
    SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
    List<SseEmitter> ownerEmitters =
        emitters.computeIfAbsent(owner, ignored -> new CopyOnWriteArrayList<>());
    ownerEmitters.add(emitter);
    emitter.onCompletion(() -> remove(owner, emitter));
    emitter.onTimeout(() -> remove(owner, emitter));
    emitter.onError(error -> remove(owner, emitter));
    try {
      emitter.send(SseEmitter.event().name("connected").data("ok"));
      if (!snapshot.isEmpty()) {
        emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
      }
    } catch (IOException gone) {
      remove(owner, emitter);
    }
    return emitter;
  }

  public void publish(UUID userId, AlertDto alert) {
    UUID owner = owner(userId);
    List<SseEmitter> ownerEmitters = emitters.get(owner);
    if (ownerEmitters == null) {
      return;
    }
    for (SseEmitter emitter : ownerEmitters) {
      try {
        emitter.send(SseEmitter.event().name("alert").data(alert));
      } catch (IOException | IllegalStateException gone) {
        remove(owner, emitter);
      }
    }
    log.debug("Alert sent to {} SSE client(s) for one account", ownerEmitters.size());
  }

  int activeClients() {
    return emitters.values().stream().mapToInt(List::size).sum();
  }

  private void remove(UUID owner, SseEmitter emitter) {
    emitters.computeIfPresent(
        owner,
        (ignored, list) -> {
          list.remove(emitter);
          return list.isEmpty() ? null : list;
        });
  }

  private static UUID owner(UUID userId) {
    return userId == null ? ANONYMOUS : userId;
  }
}
