package org.radarmc.link.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingLogger implements RadarLogger {
    public final List<String> lines = new CopyOnWriteArrayList<>();

    @Override
    public void info(String message) {
        lines.add("INFO " + message);
    }

    @Override
    public void warn(String message) {
        lines.add("WARN " + message);
    }

    @Override
    public void error(String message, Throwable error) {
        lines.add("ERROR " + message + (error == null ? "" : " " + error));
    }

    public boolean contains(String text) {
        return lines.stream().anyMatch(l -> l.contains(text));
    }

    public String all() {
        return String.join("\n", lines);
    }
}
