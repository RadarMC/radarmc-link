package org.radarmc.link.bungee;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.radarmc.link.core.CoreLifecycle;
import org.radarmc.link.core.JulLogger;
import org.radarmc.link.core.RadarCore;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.Role;
import org.radarmc.link.core.config.RadarConfig;
import org.radarmc.link.core.players.FloodgateHook;
import org.radarmc.link.core.proxy.RewardMessage;

public final class RadarLinkBungee extends Plugin implements Listener {
    private BungeePlatform platform;
    private CoreLifecycle lifecycle;

    @Override
    public void onEnable() {
        RadarLogger log = new JulLogger(getLogger());
        platform = new BungeePlatform(getProxy(), log, getDataFolder().toPath());
        lifecycle = new CoreLifecycle(log, getDataFolder().toPath(), CoreLifecycle.PROXY_COMMAND, this::startCore);
        getProxy().registerChannel(RewardMessage.CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new AdminCommand());
        lifecycle.start();
    }

    @Override
    public void onDisable() {
        lifecycle.stop();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        lifecycle
                .core()
                .ifPresent(core ->
                        core.onJoin(player.getUniqueId(), player.getName(), address(player.getSocketAddress())));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnected(ServerConnectedEvent event) {
        lifecycle
                .core()
                .ifPresent(core -> core.onServerConnected(event.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDisconnect(PlayerDisconnectEvent event) {
        lifecycle.core().ifPresent(core -> core.onQuit(event.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPluginMessage(PluginMessageEvent event) {
        if (!RewardMessage.CHANNEL.equals(event.getTag())) {
            return;
        }
        event.setCancelled(true);
        if (event.getSender() instanceof Server) {
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
        for (ProxiedPlayer player : getProxy().getPlayers()) {
            core.online().joined(player.getName());
        }
        core.start();
        return core;
    }

    private boolean sendToServerOf(String playerName, byte[] message) {
        ProxiedPlayer player = getProxy().getPlayer(playerName);
        if (player == null || player.getServer() == null) {
            return false;
        }
        player.getServer().sendData(RewardMessage.CHANNEL, message);
        return true;
    }

    private static InetAddress address(SocketAddress socket) {
        return socket instanceof InetSocketAddress ? ((InetSocketAddress) socket).getAddress() : null;
    }

    private final class AdminCommand extends Command {
        AdminCommand() {
            super(CoreLifecycle.PROXY_COMMAND, CoreLifecycle.PERMISSION, CoreLifecycle.PROXY_ALIAS);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            List<String> lines = CoreLifecycle.action(args).equals(CoreLifecycle.RELOAD)
                    ? Collections.singletonList(lifecycle.reload())
                    : lifecycle.status();
            lines.forEach(line -> sender.sendMessage(new TextComponent(line)));
        }
    }
}
