package com.guardpay.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * Loads the local development dotenv file regardless of which directory
 * {@code mvn spring-boot:run} is started from (repo root or {@code backend/}).
 *
 * <p>Candidates are tried in order; the first existing regular file wins.
 * Values land just below OS environment variables, so explicit environment
 * always takes precedence and {@code application.yml} defaults apply
 * otherwise. Only simple {@code KEY=value} lines are read ({@code #}
 * comments, blank lines, and {@code export} prefixes are tolerated);
 * values are used verbatim and never logged.
 */
public class DotenvPropertyInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(DotenvPropertyInitializer.class);

  static final String[] CANDIDATES = {"./backend/.env", "./.env", "../backend/.env"};

  static final String PROPERTY_SOURCE_NAME = "guardpayDotenv";

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    if (!(context.getEnvironment() instanceof StandardEnvironment environment)) {
      return;
    }
    findDotenv()
        .ifPresent(
            path -> {
              Map<String, Object> values = parse(path);
              if (!values.isEmpty()) {
                insertSource(environment.getPropertySources(), values);
                // Path only — never values.
                log.info("Loaded local environment file: {}", path.toAbsolutePath().normalize());
              }
            });
  }

  /** Inserts file values just below OS environment (which always wins). */
  static void insertSource(MutablePropertySources sources, Map<String, Object> values) {
    MapPropertySource source = new MapPropertySource(PROPERTY_SOURCE_NAME, values);
    if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
      sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
    } else {
      sources.addFirst(source);
    }
  }

  static Optional<Path> findDotenv() {
    for (String candidate : CANDIDATES) {
      Path path = Paths.get(candidate);
      if (Files.isRegularFile(path)) {
        return Optional.of(path);
      }
    }
    return Optional.empty();
  }

  static Map<String, Object> parse(Path path) {
    Map<String, Object> values = new LinkedHashMap<>();
    String content;
    try {
      content = Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      return values;
    }
    for (String rawLine : content.split("\\R")) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      if (line.startsWith("export ")) {
        line = line.substring("export ".length()).trim();
      }
      int separator = line.indexOf('=');
      if (separator <= 0) {
        continue;
      }
      String name = line.substring(0, separator).trim();
      String value = line.substring(separator + 1).trim();
      if (!name.isEmpty()) {
        values.put(name, value);
      }
    }
    return values;
  }
}
