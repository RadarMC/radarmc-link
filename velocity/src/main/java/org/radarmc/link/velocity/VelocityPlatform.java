package org.radarmc.link.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import org.radarmc.link.core.PlatformAdapter;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.ServerSnapshot;

final class VelocityPlatform implements PlatformAdapter {
    private final ProxyServer proxy;
    private final RadarLogger logger;
    private final Path dataDirectory;

    VelocityPlatform(ProxyServer proxy, RadarLogger logger, Path dataDirectory) {
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
        String software =
                proxy.getVersion().getName() + " " + proxy.getVersion().getVersion();
        return new ServerSnapshot(
                "velocity",
                software,
                proxy.getPlayerCount(),
                proxy.getConfiguration().getShowMaxPlayers());
    }

    @Override
    public void runOnMainThread(Runnable task) {
        task.run();
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command);
    }
}
