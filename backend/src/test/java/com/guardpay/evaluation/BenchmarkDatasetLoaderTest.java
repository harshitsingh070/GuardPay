package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkDatasetLoaderTest {

  private static final String VALID =
      """
      {"id":"valid","category":"test","description":"A valid scenario.","label":"FRAUD","expectedScore":20,"expectedRiskLevel":"LOW","expectedAction":"ALLOW","expectedSignals":["NEW_RECIPIENT"],"input":{"amount":10,"currency":"USD","historyAmounts":[],"recentAttempts":0,"recipient":"NEW","merchant":"NEW","contentText":null,"contentUrl":null,"device":"NONE","aiFindings":[]},"tags":["test"],"expectedAi":null}
      """;

  @TempDir Path tempDir;

  private Path write(String content) throws Exception {
    Path path = tempDir.resolve("scenarios.jsonl");
    Files.writeString(path, content, StandardCharsets.UTF_8);
    return path;
  }

  @Test
  void loadsAndValidatesAValidScenario() throws Exception {
    List<BenchmarkScenario> scenarios = new BenchmarkDatasetLoader().load(write(VALID));

    assertEquals(1, scenarios.size());
    assertEquals("valid", scenarios.get(0).id());
    assertEquals(BenchmarkLabel.FRAUD, scenarios.get(0).label());
    assertEquals(20, scenarios.get(0).expectedScore());
  }

  @Test
  void rejectsDuplicateIds() throws Exception {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BenchmarkDatasetLoader().load(write(VALID + "\n" + VALID)));

    assertTrue(error.getMessage().contains("duplicate scenario id"));
  }

  @Test
  void rejectsUnknownExpectedSignals() throws Exception {
    String invalid = VALID.replace("NEW_RECIPIENT", "NOT_A_REAL_SIGNAL");

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BenchmarkDatasetLoader().load(write(invalid)));

    assertTrue(error.getMessage().contains("unknown signal"));
  }

  @Test
  void rejectsAiFindingsWithoutContent() throws Exception {
    String invalid = VALID.replace("\"aiFindings\":[]", "\"aiFindings\":[\"URGENT_LANGUAGE\"]");

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BenchmarkDatasetLoader().load(write(invalid)));

    assertTrue(error.getMessage().contains("AI findings require content"));
  }

  @Test
  void rejectsMalformedJson() throws Exception {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BenchmarkDatasetLoader().load(write("{\"id\":")));

    assertTrue(error.getMessage().contains("Invalid benchmark JSON"));
  }

  @Test
  void rejectsMissingRequiredFields() throws Exception {
    String invalid = VALID.replace("\"category\":\"test\",", "");

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BenchmarkDatasetLoader().load(write(invalid)));

    assertTrue(error.getMessage().contains("category is required"));
  }

  @Test
  void rejectsInvalidEnums() throws Exception {
    String invalid = VALID.replace("\"label\":\"FRAUD\"", "\"label\":\"UNKNOWN\"");

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BenchmarkDatasetLoader().load(write(invalid)));

    assertTrue(error.getMessage().contains("Invalid benchmark JSON"));
  }

  @Test
  void rejectsEmptyDataset() throws Exception {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BenchmarkDatasetLoader().load(write("\n\n")));

    assertTrue(error.getMessage().contains("empty"));
  }
}
