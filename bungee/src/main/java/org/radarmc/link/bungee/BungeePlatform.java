package org.radarmc.link.bungee;

import java.nio.file.Path;
import net.md_5.bungee.api.ProxyServer;
import org.radarmc.link.core.PlatformAdapter;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.ServerSnapshot;

final class BungeePlatform implements PlatformAdapter {
    private final ProxyServer proxy;
    private final RadarLogger logger;
    private final Path dataDirectory;

    BungeePlatform(ProxyServer proxy, RadarLogger logger, Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Override
    public RadarLogger logger() {
        return logger;
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public ServerSnapshot snapshot() {
        String software = proxy.getName() + " " + proxy.getVersion();
        return new ServerSnapshot(
                "bungeecord",
                software,
                proxy.getOnlineCount(),
                proxy.getConfig().getPlayerLimit());
    }

    @Override
    public void runOnMainThread(Runnable task) {
        task.run();
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        proxy.getPluginManager().dispatchCommand(proxy.getConsole(), command);
    }
}
