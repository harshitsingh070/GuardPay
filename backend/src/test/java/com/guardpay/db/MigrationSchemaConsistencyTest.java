package com.guardpay.db;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the Flyway schema against mismatches that only surface in production,
 * where {@code ddl-auto=validate} compares the schema with the JPA entities.
 *
 * <p>Tests run on H2 with {@code create-drop}, so they can never catch a
 * missing column or a wrong type. This test reads the migrations directly and
 * accounts for columns added later by {@code ALTER TABLE ... ADD COLUMN}.
 */
class MigrationSchemaConsistencyTest {

  private static final Path MIGRATIONS =
      Path.of("src", "main", "resources", "db", "migration");
  private static final Pattern CREATE_TABLE =
      Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)\\s*\\(");
  private static final Pattern ALTER_TABLE =
      Pattern.compile("ALTER\\s+TABLE\\s+(\\w+)");
  private static final Pattern ADD_COLUMN =
      Pattern.compile("ADD\\s+COLUMN\\s+(\\w+)");
  private static final Pattern TABLE_END = Pattern.compile("^\\s*\\)");
  private static final Pattern COLUMN =
      Pattern.compile("^\\s+(\\w+)\\s+\\w");

  @Test
  void everyCreatedTableHasBaseEntityAuditColumns() throws IOException {
    Map<String, Set<String>> tables = readTables();

    List<String> missing = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : tables.entrySet()) {
      Set<String> columns = entry.getValue();
      if (!columns.contains("created_at") || !columns.contains("updated_at")) {
        missing.add(
            entry.getKey()
                + " missing "
                + (columns.contains("created_at") ? "" : "created_at ")
                + (columns.contains("updated_at") ? "" : "updated_at"));
      }
    }

    assertTrue(
        missing.isEmpty(),
        "Every table must carry BaseEntity audit columns so ddl-auto=validate passes:\n"
            + String.join("\n", missing));
  }

  @Test
  void criticalBehavioralTablesExist() throws IOException {
    Map<String, Set<String>> tables = readTables();

    assertTrue(tables.containsKey("user_behavior_profiles"), "behavioral profile table missing");
    assertTrue(tables.containsKey("behavioral_outcome_events"), "outcome ledger table missing");
    assertTrue(
        tables.get("behavioral_outcome_events").contains("learnable"),
        "outcome ledger must record learnability");
    assertTrue(
        tables.get("user_behavior_profiles").contains("profile_version"),
        "profile version column missing");
  }

  private static Map<String, Set<String>> readTables() throws IOException {
    Map<String, Set<String>> tables = new LinkedHashMap<>();

    for (Path migration : Files.list(MIGRATIONS).sorted().toList()) {
      if (!migration.toString().endsWith(".sql")) {
        continue;
      }
      parse(Files.readAllLines(migration), tables);
    }
    return tables;
  }

  private static void parse(List<String> lines, Map<String, Set<String>> tables) {
    String createTarget = null;
    String alterTarget = null;

    for (String line : lines) {
      Matcher create = CREATE_TABLE.matcher(line);
      if (create.find()) {
        createTarget = create.group(1);
        alterTarget = null;
        tables.computeIfAbsent(createTarget, key -> new LinkedHashSet<>());
        continue;
      }

      Matcher alter = ALTER_TABLE.matcher(line);
      if (alter.find()) {
        alterTarget = alter.group(1);
        createTarget = null;
        tables.computeIfAbsent(alterTarget, key -> new LinkedHashSet<>());
      }

      Matcher added = ADD_COLUMN.matcher(line);
      if (added.find() && alterTarget != null) {
        tables.get(alterTarget).add(added.group(1));
      }

      if (createTarget != null) {
        if (TABLE_END.matcher(line).find()) {
          createTarget = null;
        } else {
          Matcher column = COLUMN.matcher(line);
          if (column.find()) {
            tables.get(createTarget).add(column.group(1));
          }
        }
      }

      if (line.contains(";")) {
        alterTarget = null;
      }
    }
  }
}
