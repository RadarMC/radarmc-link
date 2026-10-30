package org.radarmc.link.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.radarmc.link.core.BuildInfo;
import org.radarmc.link.core.CoreLifecycle;
import org.radarmc.link.core.RadarCore;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.Role;
import org.radarmc.link.core.config.RadarConfig;
import org.radarmc.link.core.players.FloodgateHook;
import org.radarmc.link.core.proxy.RewardMessage;
import org.slf4j.Logger;

@Plugin(
        id = "radarlink",
        name = "RadarLink",
        version = BuildInfo.VERSION,
        url = "https://radarmc.org",
        description = "Links your network to RadarMC: confirms that players who copied the IP joined, and gives vote"
                + " streak rewards.",
        authors = {"RadarMC"},
        dependencies = {@Dependency(id = "floodgate", optional = true)})
public final class RadarLinkVelocity {
    static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(RewardMessage.CHANNEL);
    private static final String DATA_FOLDER = "RadarLink";

    private final ProxyServer proxy;
    private final VelocityPlatform platform;
    private final CoreLifecycle lifecycle;

    @Inject
    public RadarLinkVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        RadarLogger log = new Slf4jLogger(logger);
        Path dataFolder = dataDirectory.resolveSibling(DATA_FOLDER);
        this.platform = new VelocityPlatform(proxy, log, dataFolder);
        this.lifecycle = new CoreLifecycle(log, dataFolder, CoreLifecycle.PROXY_COMMAND, this::startCore);
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        proxy.getCommandManager()
                .register(
                        proxy.getCommandManager()
                                .metaBuilder(CoreLifecycle.PROXY_COMMAND)
                                .aliases(CoreLifecycle.PROXY_ALIAS)
                                .plugin(this)
                                .build(),
                        new AdminCommand());
        lifecycle.start();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        lifecycle.stop();
    }

    @Subscribe(order = PostOrder.LAST)
    public void onLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        lifecycle
                .core()
                .ifPresent(core -> core.onJoin(
                        player.getUniqueId(),
                        player.getUsername(),
                        player.getRemoteAddress().getAddress()));
    }

    @Subscribe(order = PostOrder.LAST)
    public void onServerConnected(ServerPostConnectEvent event) {
        lifecycle
                .core()
                .ifPresent(core -> core.onServerConnected(event.getPlayer().getUsername()));
    }

    @Subscribe(order = PostOrder.LAST)
    public void onDisconnect(DisconnectEvent event) {
        lifecycle.core().ifPresent(core -> core.onQuit(event.getPlayer().getUsername()));
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (event.getSource() instanceof ServerConnection) {
            lifecycle
                    .core()
                    .flatMap(RadarCore::forwarding)
                    .ifPresent(forwarding -> forwarding.onServerMessage(event.getData()));
        }
    }

    private RadarCore startCore(RadarConfig config) {
        RadarCore core = RadarCore.builder(platform, config, Role.PROXY)
                .floodgate(FloodgateHook.detect(getClass().getClassLoader()))
                .transport(this::sendToServerOf)
                .build();
        for (Player player : proxy.getAllPlayers()) {
            core.online().joined(player.getUsername());
        }
        core.start();
        return core;
    }

    private boolean sendToServerOf(String playerName, byte[] message) {
        return proxy.getPlayer(playerName)
                .flatMap(Player::getCurrentServer)
                .map(connection -> connection.sendPluginMessage(CHANNEL, message))
                .orElse(false);
    }

    private final class AdminCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            List<String> lines = CoreLifecycle.action(invocation.arguments()).equals(CoreLifecycle.RELOAD)
                    ? Collections.singletonList(lifecycle.reload())
                    : lifecycle.status();
            lines.forEach(line -> invocation.source().sendMessage(Component.text(line)));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission(CoreLifecycle.PERMISSION);
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            return invocation.arguments().length <= 1
                    ? new ArrayList<>(CoreLifecycle.ACTIONS)
                    : Collections.<String>emptyList();
        }
    }
}
