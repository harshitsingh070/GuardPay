package com.guardpay.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.RiskLevels;
import com.guardpay.risk.RiskSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Loads and validates the JSONL benchmark dataset before any evaluation starts. */
public final class BenchmarkDatasetLoader {

  private final ObjectMapper mapper;

  public BenchmarkDatasetLoader() {
    this(new ObjectMapper());
  }

  public BenchmarkDatasetLoader(ObjectMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  public List<BenchmarkScenario> load(Path path) throws IOException {
    Objects.requireNonNull(path, "path must not be null");
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      return load(reader, path.toString());
    }
  }

  public List<BenchmarkScenario> load(Reader reader, String source) throws IOException {
    Objects.requireNonNull(reader, "reader must not be null");
    String origin = source == null ? "<reader>" : source;
    List<BenchmarkScenario> scenarios = new ArrayList<>();
    Set<String> ids = new HashSet<>();

    try (BufferedReader buffered =
        reader instanceof BufferedReader existing ? existing : new BufferedReader(reader)) {
      String line;
      int lineNumber = 0;
      while ((line = buffered.readLine()) != null) {
        lineNumber++;
        if (lineNumber == 1 && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
          line = line.substring(1);
        }
        if (line.isBlank()) {
          continue;
        }
        BenchmarkScenario scenario = parse(line, origin, lineNumber);
        validate(scenario, origin, lineNumber);
        if (!ids.add(scenario.id())) {
          invalid(origin, lineNumber, "duplicate scenario id: " + scenario.id());
        }
        scenarios.add(scenario);
      }
    }

    if (scenarios.isEmpty()) {
      throw new IllegalArgumentException("Benchmark dataset is empty: " + origin);
    }
    return List.copyOf(scenarios);
  }

  private BenchmarkScenario parse(String line, String origin, int lineNumber) {
    try {
      return mapper.readValue(line, BenchmarkScenario.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(
          "Invalid benchmark JSON at " + origin + ":" + lineNumber + ": " + ex.getOriginalMessage(),
          ex);
    }
  }

  private static void validate(BenchmarkScenario scenario, String origin, int lineNumber) {
    if (scenario == null) {
      invalid(origin, lineNumber, "scenario must not be null");
    }
    requireText(scenario.id(), "id", origin, lineNumber);
    requireText(scenario.category(), "category", origin, lineNumber);
    requireText(scenario.description(), "description", origin, lineNumber);
    if (scenario.label() == null) {
      invalid(origin, lineNumber, "label is required");
    }
    validateSignals(scenario.expectedSignals(), "expectedSignals", origin, lineNumber);
    validateOutcome(
        scenario.expectedScore(),
        scenario.expectedRiskLevel(),
        scenario.expectedAction(),
        "expected",
        origin,
        lineNumber);

    if (scenario.tags() == null || scenario.tags().isEmpty()) {
      invalid(origin, lineNumber, "tags must contain at least one value");
    }
    for (String tag : scenario.tags()) {
      requireText(tag, "tags[]", origin, lineNumber);
    }

    BenchmarkScenario.Input input = scenario.input();
    if (input == null) {
      invalid(origin, lineNumber, "input is required");
    }
    if (input.amount() == null || input.amount().signum() <= 0) {
      invalid(origin, lineNumber, "input.amount must be positive");
    }
    requireText(input.currency(), "input.currency", origin, lineNumber);
    if (input.historyAmounts() == null) {
      invalid(origin, lineNumber, "input.historyAmounts is required (use [] when empty)");
    }
    for (BigDecimal amount : input.historyAmounts()) {
      if (amount == null || amount.signum() <= 0) {
        invalid(origin, lineNumber, "input.historyAmounts values must be positive");
      }
    }
    if (input.recentAttempts() == null || input.recentAttempts() < 0) {
      invalid(origin, lineNumber, "input.recentAttempts must be >= 0");
    }
    if (input.recipient() == null) {
      invalid(origin, lineNumber, "input.recipient is required");
    }
    if (input.merchant() == null) {
      invalid(origin, lineNumber, "input.merchant is required");
    }
    if (input.device() == null) {
      invalid(origin, lineNumber, "input.device is required");
    }
    if (input.aiFindings() == null) {
      invalid(origin, lineNumber, "input.aiFindings is required (use [] when empty)");
    }
    if (!input.aiFindings().isEmpty() && !hasContent(input)) {
      invalid(origin, lineNumber, "AI findings require contentText or contentUrl");
    }
    for (String code : input.aiFindings()) {
      validateAiCode(code, origin, lineNumber);
    }

    BenchmarkScenario.ExpectedAi expectedAi = scenario.expectedAi();
    if (expectedAi != null) {
      if (input.aiFindings().isEmpty()) {
        invalid(origin, lineNumber, "expectedAi requires at least one aiFinding");
      }
      validateSignals(expectedAi.signals(), "expectedAi.signals", origin, lineNumber);
      validateOutcome(
          expectedAi.score(),
          expectedAi.riskLevel(),
          expectedAi.action(),
          "expectedAi",
          origin,
          lineNumber);
    } else if (!input.aiFindings().isEmpty()) {
      invalid(origin, lineNumber, "expectedAi is required when aiFindings are supplied");
    }
  }

  private static void validateOutcome(
      Integer score,
      RiskLevel level,
      RecommendedAction action,
      String prefix,
      String origin,
      int lineNumber) {
    if (score == null || score < 0 || score > RiskLevels.MAX_SCORE) {
      invalid(origin, lineNumber, prefix + " score must be between 0 and 100");
    }
    if (level == null) {
      invalid(origin, lineNumber, prefix + " risk level is required");
    }
    if (action == null) {
      invalid(origin, lineNumber, prefix + " action is required");
    }
    RiskLevel derivedLevel = RiskLevels.levelFor(score);
    if (level != derivedLevel) {
      invalid(
          origin,
          lineNumber,
          prefix + " risk level " + level + " does not match score " + score + " (" + derivedLevel + ")");
    }
    RecommendedAction derivedAction = RiskLevels.actionFor(level);
    if (action != derivedAction) {
      invalid(
          origin,
          lineNumber,
          prefix + " action " + action + " does not match level " + level + " (" + derivedAction + ")");
    }
  }

  private static void validateSignals(
      List<String> signals, String field, String origin, int lineNumber) {
    if (signals == null) {
      invalid(origin, lineNumber, field + " is required (use [] when empty)");
    }
    Set<String> seen = new HashSet<>();
    for (String signal : signals) {
      if (signal == null || signal.isBlank()) {
        invalid(origin, lineNumber, field + " must not contain blank values");
      }
      if (!seen.add(signal)) {
        invalid(origin, lineNumber, field + " contains duplicate signal: " + signal);
      }
      try {
        RiskSignal.valueOf(signal);
      } catch (IllegalArgumentException ex) {
        invalid(origin, lineNumber, field + " contains unknown signal: " + signal);
      }
    }
  }

  private static void validateAiCode(String code, String origin, int lineNumber) {
    if (code == null || code.isBlank()) {
      invalid(origin, lineNumber, "input.aiFindings must not contain blank values");
    }
    try {
      PaymentSignalType type = PaymentSignalType.valueOf(code);
      if (type == PaymentSignalType.ACTIVE_CALL) {
        invalid(origin, lineNumber, "AI cannot emit ACTIVE_CALL");
      }
    } catch (IllegalArgumentException ex) {
      invalid(origin, lineNumber, "input.aiFindings contains unknown code: " + code);
    }
  }

  private static boolean hasContent(BenchmarkScenario.Input input) {
    return hasText(input.contentText()) || hasText(input.contentUrl());
  }

  private static void requireText(String value, String field, String origin, int lineNumber) {
    if (!hasText(value)) {
      invalid(origin, lineNumber, field + " is required");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static void invalid(String origin, int lineNumber, String message) {
    throw new IllegalArgumentException("Invalid benchmark scenario at " + origin + ":" + lineNumber + ": " + message);
  }
}
