package org.radarmc.link.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConfigResult {
    private final RadarConfig config;
    private final List<String> warnings;
    private final List<String> errors;

    ConfigResult(RadarConfig config, List<String> warnings, List<String> errors) {
        this.config = config;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    static ConfigResult failed(String error) {
        return new ConfigResult(null, Collections.<String>emptyList(), Collections.singletonList(error));
    }

    public RadarConfig config() {
        return config;
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<String> errors() {
        return errors;
    }

    public boolean usable() {
        return config != null && errors.isEmpty();
    }
}
