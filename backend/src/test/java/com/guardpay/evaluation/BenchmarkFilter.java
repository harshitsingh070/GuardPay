package com.guardpay.evaluation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Optional scenario selector matching a scenario id, category, or label. */
public record BenchmarkFilter(Set<String> values) {

  public BenchmarkFilter {
    values = values == null ? Set.of() : Set.copyOf(values);
  }

  public static BenchmarkFilter none() {
    return new BenchmarkFilter(Set.of());
  }

  public static BenchmarkFilter parse(String expression) {
    if (expression == null || expression.isBlank()) {
      return none();
    }
    Set<String> values =
        Arrays.stream(expression.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.toUpperCase(java.util.Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    return new BenchmarkFilter(values);
  }

  public boolean matches(BenchmarkScenario scenario) {
    if (values.isEmpty()) {
      return true;
    }
    String id = scenario.id().toUpperCase(java.util.Locale.ROOT);
    String category = scenario.category().toUpperCase(java.util.Locale.ROOT);
    return values.contains(id)
        || values.contains(category)
        || values.contains(scenario.label().name().toUpperCase(java.util.Locale.ROOT));
  }
}
