package org.radarmc.link.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.io.TextFiles;

public final class ConfigFiles {
    public static final String CONFIG_NAME = "config.yml";
    private static final String DEFAULT_RESOURCE = "/radarlink-default-config.yml";
    private static final int MAX_DEFAULT_BYTES = 64 * 1024;

    private ConfigFiles() {}

    public static ConfigResult loadOrCreate(Path dataDirectory, RadarLogger log) {
        Path file = dataDirectory.resolve(CONFIG_NAME);
        if (Files.exists(file)) {
            migrate(dataDirectory, file, log);
        } else {
            create(dataDirectory, file, log);
        }
        ConfigResult result = ConfigLoader.load(file);
        result.warnings().forEach(warning -> log.warn(CONFIG_NAME + ": " + warning));
        result.errors().forEach(error -> log.error(CONFIG_NAME + ": " + error));
        return result;
    }

    private static void create(Path dataDirectory, Path file, RadarLogger log) {
        try {
            Files.createDirectories(dataDirectory);
            TextFiles.writeUtf8(file, defaults());
            log.info("Created " + CONFIG_NAME + ": paste your API key and server slug into it, then restart or run"
                    + " /radarlink reload (/radarlinkproxy reload on a proxy)");
        } catch (IOException e) {
            log.error("Cannot create " + CONFIG_NAME, e);
        }
    }

    private static void migrate(Path dataDirectory, Path file, RadarLogger log) {
        try {
            Optional<ConfigMigrator.Migration> migration = ConfigMigrator.migrate(TextFiles.readUtf8(file), defaults());
            if (migration.isPresent()) {
                write(dataDirectory, file, migration.get(), log);
            }
        } catch (IOException e) {
            log.warn("Cannot update " + CONFIG_NAME + " to format " + ConfigMigrator.CURRENT_VERSION + " ("
                    + e.getMessage() + "); it is read as it is");
        }
    }

    private static void write(Path dataDirectory, Path file, ConfigMigrator.Migration migration, RadarLogger log)
            throws IOException {
        String backup = CONFIG_NAME + ".v" + migration.fromVersion() + ".bak";
        Files.copy(file, dataDirectory.resolve(backup), StandardCopyOption.REPLACE_EXISTING);
        TextFiles.writeUtf8Atomically(file, migration.text());
        log.info("Updated " + CONFIG_NAME + " to format " + ConfigMigrator.CURRENT_VERSION + "; the old copy is "
                + backup);
        migration.notes().forEach(note -> log.info(CONFIG_NAME + ": " + note));
    }

    private static String defaults() throws IOException {
        try (InputStream in = ConfigFiles.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IOException("default config missing from the jar");
            }
            return TextFiles.readUtf8(in, MAX_DEFAULT_BYTES);
        }
    }
}
