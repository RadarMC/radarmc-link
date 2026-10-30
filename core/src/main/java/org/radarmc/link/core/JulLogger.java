package org.radarmc.link.core;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class JulLogger implements RadarLogger {
    private final Logger logger;

    public JulLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warning(message);
    }

    @Override
    public void error(String message, Throwable error) {
        if (error == null) {
            logger.severe(message);
        } else {
            logger.log(Level.SEVERE, message, error);
        }
    }
}
