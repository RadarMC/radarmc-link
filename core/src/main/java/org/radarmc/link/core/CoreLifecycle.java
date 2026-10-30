package org.radarmc.link.core;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.radarmc.link.core.config.ConfigFiles;
import org.radarmc.link.core.config.ConfigResult;
import org.radarmc.link.core.config.RadarConfig;

public final class CoreLifecycle {
    public static final String PERMISSION = "radarlink.admin";
    public static final String PROXY_COMMAND = "radarlinkproxy";
    public static final String PROXY_ALIAS = "rlinkproxy";
    public static final String STATUS = "status";
    public static final String RELOAD = "reload";
    public static final List<String> ACTIONS = Collections.unmodifiableList(Arrays.asList(STATUS, RELOAD));

    private final RadarLogger log;
    private final Path dataDirectory;
    private final String command;
    private final Function<RadarConfig, RadarCore> starter;
    private volatile RadarCore core;

    public CoreLifecycle(
            RadarLogger log, Path dataDirectory, String command, Function<RadarConfig, RadarCore> starter) {
        this.log = log;
        this.dataDirectory = dataDirectory;
        this.command = command;
        this.starter = starter;
    }

    public static String action(String[] args) {
        return args.length == 0 ? STATUS : args[0].toLowerCase(Locale.ROOT);
    }

    public synchronized void start() {
        ConfigResult result = ConfigFiles.loadOrCreate(dataDirectory, log);
        if (!result.usable()) {
            log.warn("RadarLink is idle until config.yml is fixed");
            return;
        }
        core = starter.apply(result.config());
    }

    public synchronized void stop() {
        RadarCore current = core;
        core = null;
        if (current != null) {
            current.stop();
        }
    }

    public Optional<RadarCore> core() {
        return Optional.ofNullable(core);
    }

    public String reload() {
        stop();
        start();
        return reloadReply();
    }

    public String reloadReply() {
        return core == null ? "RadarLink: config.yml has errors, see the console" : "RadarLink: reloaded";
    }

    public List<String> status() {
        RadarCore current = core;
        if (current == null) {
            return Collections.singletonList(
                    "RadarLink is not connected: fix config.yml and run /" + command + " reload");
        }
        return current.status();
    }
}
