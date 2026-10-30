package org.radarmc.link.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.radarmc.link.core.io.TextFiles;
import org.radarmc.link.core.text.Strings;

final class InstanceId {
    static final String FILE_NAME = "instance-id";

    private InstanceId() {}

    static String load(Path dataDirectory, RadarLogger log) {
        Path file = dataDirectory.resolve(FILE_NAME);
        try {
            if (Files.exists(file)) {
                return UUID.fromString(Strings.strip(TextFiles.readUtf8(file))).toString();
            }
        } catch (IOException | IllegalArgumentException e) {
            log.warn("The instance id file is unreadable; creating a new one");
        }
        String id = UUID.randomUUID().toString();
        try {
            Files.createDirectories(dataDirectory);
            TextFiles.writeUtf8(file, id + "\n");
        } catch (IOException e) {
            log.error("Cannot save the instance id", e);
        }
        return id;
    }
}
