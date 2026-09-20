package com.animalin;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+(?:_\\d+)?)__.*\\.sql$");

    @Test
    void eachFlywayMigrationHasAUniqueVersion() throws Exception {
        Set<String> sourceFiles = assertUniqueVersions(sourceMigrationDir(), "source");
        Set<String> classpathFiles = assertUniqueVersions(classpathMigrationDir(), "classpath");

        assertThat(classpathFiles)
                .as("Classpath db/migration must match source; stale copies in target/classes cause Flyway duplicates")
                .containsExactlyInAnyOrderElementsOf(sourceFiles);
        assertThat(sourceFiles).contains(
                "V1__init_schema.sql",
                "V2__seed_catalog.sql",
                "V3__medications_audit_columns.sql"
        );
        assertThat(sourceFiles)
                .noneMatch(name -> name.equals("V3__add_audit_columns_to_medications.sql"));
    }

    private static Set<String> assertUniqueVersions(Path dir, String origin) throws Exception {
        assertThat(dir).as("db/migration (%s) must exist", origin).isDirectory();
        Map<String, String> versions = new HashMap<>();
        Set<String> names;

        try (Stream<Path> files = Files.list(dir)) {
            names = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        for (String name : names) {
            Matcher matcher = VERSIONED.matcher(name);
            assertThat(matcher.matches())
                    .as("Flyway file %s (%s) must use V<version>__description.sql", name, origin)
                    .isTrue();
            String previous = versions.put(matcher.group(1), name);
            assertThat(previous)
                    .as("Duplicate Flyway version %s in %s: %s and %s", matcher.group(1), origin, previous, name)
                    .isNull();
        }

        assertThat(versions).containsKeys("1", "2", "3");
        return names;
    }

    private static Path classpathMigrationDir() throws Exception {
        URI uri = Objects.requireNonNull(
                FlywayMigrationVersionTest.class.getClassLoader().getResource("db/migration"),
                "db/migration not found on classpath"
        ).toURI();
        return Paths.get(uri);
    }

    private static Path sourceMigrationDir() {
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve("src/main/resources/db/migration");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return cwd.resolve("backend/src/main/resources/db/migration");
    }
}
