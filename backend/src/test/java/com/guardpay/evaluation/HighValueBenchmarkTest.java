package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HighValueBenchmarkTest {

  private static final double DELTA = 0.000001;

  @Test
  void comparesHighValueEngineEnabledAndDisabled() throws IOException {
    Path dataset =
        Path.of("src", "test", "resources", "fraud-benchmark", "high-value", "scenarios.jsonl");
    Path output = Path.of("target", "high-value-benchmark");

    HighValueBenchmarkRunner.HighValueBenchmarkReport report =
        new HighValueBenchmarkRunner().run(dataset, output);

    assertEquals(10, report.scenarioCount());
    assertEquals(10, report.withHighValue().size());
    assertEquals(10, report.withoutHighValue().size());

    HighValueBenchmarkRunner.HighValueMetrics enabled =
        report.metrics().get("WITH_HIGH_VALUE");
    assertNotNull(enabled);
    assertEquals(5, enabled.truePositives());
    assertEquals(0, enabled.falseNegatives());
    assertEquals(0, enabled.falsePositives());
    assertEquals(5, enabled.trueNegatives());
    assertEquals(1.0, enabled.precision(), DELTA);
    assertEquals(1.0, enabled.recall(), DELTA);
    assertEquals(1.0, enabled.f1(), DELTA);
    assertEquals(0.0, enabled.falsePositiveRate(), DELTA);
    assertEquals(5, enabled.highValueFindings());
    assertEquals(2, enabled.coldStartCount());

    HighValueBenchmarkRunner.HighValueMetrics disabled =
        report.metrics().get("WITHOUT_HIGH_VALUE");
    assertNotNull(disabled);
    assertEquals(0, disabled.truePositives());
    assertEquals(5, disabled.falseNegatives());
    assertEquals(0, disabled.falsePositives());
    assertEquals(5, disabled.trueNegatives());
    assertNull(disabled.precision());
    assertEquals(0.0, disabled.recall(), DELTA);
    assertEquals(0, disabled.highValueFindings());
    assertEquals(10, disabled.coldStartCount());

    assertTrue(Files.exists(output.resolve("high-value-report.json")));
    assertTrue(Files.exists(output.resolve("high-value-report.md")));
    String markdown = Files.readString(output.resolve("high-value-report.md"));
    assertTrue(markdown.contains("## Engine comparison"));
    assertTrue(markdown.contains("HV01-new-recipient-large"));
    assertTrue(markdown.contains("WITHOUT_HIGH_VALUE"));

    String json = Files.readString(output.resolve("high-value-report.json"));
    assertTrue(json.contains("\"datasetSha256\""));
    assertTrue(json.contains("\"coldStartCount\""));
    assertFalse(json.contains("\"error\""));
  }
}
