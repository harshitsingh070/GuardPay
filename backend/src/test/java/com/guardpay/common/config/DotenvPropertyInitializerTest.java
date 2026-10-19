package com.guardpay.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.env.StandardEnvironment;

/**
 * Dotenv loading without touching the real backend/.env (which may hold
 * actual secrets — tests use temp files only).
 */
class DotenvPropertyInitializerTest {

  @Test
  void parsesSimpleKeyValueLines(@TempDir Path dir) throws Exception {
    Path file =
        Files.writeString(
            dir.resolve("t.env"),
            "# comment\n\nA=1\nB = two \nexport C=three\nno-equals-here\n=noname\n",
            StandardCharsets.UTF_8);

    Map<String, Object> values = DotenvPropertyInitializer.parse(file);

    assertEquals("1", values.get("A"));
    assertEquals("two", values.get("B"));
    assertEquals("three", values.get("C"));
    assertEquals(3, values.size());
  }

  @Test
  void unreadableFileYieldsEmpty(@TempDir Path dir) {
    Map<String, Object> values =
        DotenvPropertyInitializer.parse(dir.resolve("does-not-exist.env"));

    assertTrue(values.isEmpty());
  }

  @Test
  void fileValuesSitBelowSystemEnvironment() {
    // Stand-in for the OS environment source (same name the initializer keys on).
    MutablePropertySources sources = new MutablePropertySources();
    sources.addLast(
        new MapPropertySource(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            Map.of("SHARED", "env-wins")));

    DotenvPropertyInitializer.insertSource(
        sources, Map.of("SHARED", "file-loses", "ONLY_FILE", "yes"));

    // Resolved through Spring's real ordering: env wins, file fills gaps.
    PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);
    assertEquals("env-wins", resolver.getProperty("SHARED"));
    assertEquals("yes", resolver.getProperty("ONLY_FILE"));
  }
}
