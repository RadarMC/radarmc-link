package org.radarmc.link.bukkit;

import java.nio.file.Path;
import org.bukkit.Bukkit;
import org.radarmc.link.core.JulLogger;
import org.radarmc.link.core.PlatformAdapter;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.ServerSnapshot;

final class BukkitPlatform implements PlatformAdapter {
    private static final String[] PAPER_CLASSES = {
        "io.papermc.paper.configuration.Configuration",
        "com.destroystokyo.paper.PaperConfig",
        "org.github.paperspigot.PaperSpigotConfig",
    };
    private static final String CRAFTBUKKIT = "CraftBukkit";

    private final RadarLinkBukkit plugin;
    private final ServerScheduler scheduler;
    private final RadarLogger logger;
    private final String platform;
    private final String software;

    BukkitPlatform(RadarLinkBukkit plugin, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = new JulLogger(plugin.getLogger());
        this.platform = detectPlatform();
        this.software = describeSoftware(platform);
    }

    @Override
    public RadarLogger logger() {
        return logger;
    }

    @Override
    public Path dataDirectory() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public ServerSnapshot snapshot() {
        return new ServerSnapshot(
                platform, software, BukkitCompat.onlinePlayers().size(), Bukkit.getMaxPlayers());
    }

    @Override
    public void runOnMainThread(Runnable task) {
        scheduler.runGlobal(task);
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    static String detectPlatform() {
        if (ServerScheduler.isFolia()) {
            return "folia";
        }
        for (String name : PAPER_CLASSES) {
            if (BukkitCompat.hasClass(name)) {
                return "paper";
            }
        }
        return "spigot";
    }

    private static String describeSoftware(String platform) {
        String version = Bukkit.getBukkitVersion();
        int dash = version.indexOf('-');
        return serverName(platform) + " " + (dash > 0 ? version.substring(0, dash) : version);
    }

    private static String serverName(String platform) {
        String name = Bukkit.getName();
        if (!CRAFTBUKKIT.equals(name)) {
            return name;
        }
        if (platform.equals("paper")) {
            return "Paper";
        }
        return Bukkit.getVersion().contains("Spigot") ? "Spigot" : name;
    }
}
