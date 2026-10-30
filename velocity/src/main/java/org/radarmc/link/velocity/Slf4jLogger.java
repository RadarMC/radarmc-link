package org.radarmc.link.velocity;

import org.radarmc.link.core.RadarLogger;
import org.slf4j.Logger;

final class Slf4jLogger implements RadarLogger {
    private final Logger logger;

    Slf4jLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message, Throwable error) {
        if (error == null) {
            logger.error(message);
        } else {
            logger.error(message, error);
        }
    }
}
