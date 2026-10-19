package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;

/**
 * Device-signal collection: fresh/stale/unknown handling, +15 contribution,
 * and the trust boundary (the automatic DTO exposes no risk boolean).
 */
@ExtendWith(MockitoExtension.class)
class PaymentSignalCollectorDeviceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private UserRepository users;
  @Mock private RecipientRepository recipients;
  @Mock private MerchantRepository merchants;
  @Mock private TransactionRepository transactions;

  private PaymentSignalCollectorService collector;
  private User user;

  @BeforeEach
  void setUp() {
    collector = new PaymentSignalCollectorService(users, recipients, merchants, transactions);
    user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    // Lenient: the structural test below never collects, behavior tests always do.
    lenient().when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    lenient()
        .when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of());
  }

  private SignalCollectionRequest request() {
    return new SignalCollectionRequest(new BigDecimal("45"), null, USER_ID, null, null, null);
  }

  private PaymentSignal activeCall(AutomaticSignalCollectionResult result) {
    return result.signals().stream()
        .filter(signal -> signal.type() == PaymentSignalType.ACTIVE_CALL)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void freshActiveDeviceCreatesSignal() {
    AutomaticSignalCollectionResult result =
        collector.collect(request(), new DeviceCallSignal(true, null));

    PaymentSignal signal = activeCall(result);
    assertTrue(signal.detected());
    assertEquals(SignalSource.DEVICE, signal.source());
    assertNull(signal.reason());
    assertTrue(result.detected(PaymentSignalType.ACTIVE_CALL));
  }

  @Test
  void freshIdleDeviceCreatesNoSignal() {
    AutomaticSignalCollectionResult result =
        collector.collect(request(), new DeviceCallSignal(false, null));

    PaymentSignal signal = activeCall(result);
    assertFalse(signal.detected());
    assertEquals(SignalSource.DEVICE, signal.source());
    assertNull(signal.reason());
  }

  @Test
  void staleDeviceIsUnavailable() {
    AutomaticSignalCollectionResult result =
        collector.collect(request(), new DeviceCallSignal(null, "STALE"));

    assertFalse(result.detected(PaymentSignalType.ACTIVE_CALL));
    assertEquals("STALE", activeCall(result).reason());
  }

  @Test
  void unknownDeviceIsUnavailable() {
    AutomaticSignalCollectionResult result =
        collector.collect(request(), new DeviceCallSignal(null, "NOT_AVAILABLE"));

    assertFalse(result.detected(PaymentSignalType.ACTIVE_CALL));
    assertEquals("NOT_AVAILABLE", activeCall(result).reason());
  }

  @Test
  void automaticRequestExposesNoRiskBoolean() {
    // A malicious client cannot send activeCall=true: the field does not exist.
    boolean hasFlag =
        Arrays.stream(AutomaticEvaluationRequestDto.class.getDeclaredMethods())
            .map(Method::getName)
            .anyMatch(name -> name.equalsIgnoreCase("getActiveCall")
                || name.equalsIgnoreCase("isActiveCall")
                || name.equalsIgnoreCase("setActiveCall"));
    assertFalse(hasFlag);
  }
}
