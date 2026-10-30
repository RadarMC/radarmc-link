package org.radarmc.link.core;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Function;
import org.radarmc.link.core.api.ApiClient;
import org.radarmc.link.core.api.ApiException;
import org.radarmc.link.core.api.Heartbeat;

final class HeartbeatSender {
    private final ApiClient api;
    private final Function<Boolean, Heartbeat> payload;
    private final Clock clock;
    private final RadarLogger log;
    private volatile Instant lastSuccess;
    private volatile String lastError;
    private boolean rejectionWarned;

    HeartbeatSender(ApiClient api, Function<Boolean, Heartbeat> payload, Clock clock, RadarLogger log) {
        this.api = api;
        this.payload = payload;
        this.clock = clock;
        this.log = log;
    }

    void send(boolean stopping) {
        try {
            api.heartbeat(payload.apply(stopping));
            lastSuccess = clock.instant();
            lastError = null;
            rejectionWarned = false;
        } catch (ApiException e) {
            lastError = e.describe();
            failed(e, stopping);
        } catch (RuntimeException e) {
            log.error("Heartbeat failed", e);
        }
    }

    String status() {
        return "Last heartbeat: " + (lastSuccess == null ? "never" : lastSuccess.toString())
                + (lastError == null ? "" : ", last error: " + lastError);
    }

    private void failed(ApiException e, boolean stopping) {
        if (!e.rejectedCredentials()) {
            if (!stopping) {
                log.warn("Heartbeat failed: " + e.describe());
            }
            return;
        }
        if (!rejectionWarned) {
            rejectionWarned = true;
            log.error("RadarMC rejected the connection (" + e.describe()
                    + "). Check api-key and server-slug in config.yml.");
        }
    }
}
