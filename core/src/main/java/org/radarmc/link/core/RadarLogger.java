package org.radarmc.link.core;

public interface RadarLogger {
    void info(String message);

    void warn(String message);

    void error(String message, Throwable error);

    default void error(String message) {
        error(message, null);
    }
}
