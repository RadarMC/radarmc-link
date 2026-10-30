package org.radarmc.link.bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.radarmc.link.core.CoreLifecycle;
import org.radarmc.link.core.RadarCore;
import org.radarmc.link.core.Role;
import org.radarmc.link.core.config.RadarConfig;
import org.radarmc.link.core.players.FloodgateHook;
import org.radarmc.link.core.proxy.RewardMessage;
import org.radarmc.link.core.proxy.RewardReceiver;

public class RadarLinkBukkit extends JavaPlugin {
    private static final String COMMAND = "radarlink";

    private ServerScheduler scheduler;
    private BukkitPlatform platform;
    private CoreLifecycle lifecycle;

    @Override
    public void onEnable() {
        scheduler = ServerScheduler.create(this);
        platform = new BukkitPlatform(this, scheduler);
        lifecycle = new CoreLifecycle(platform.logger(), platform.dataDirectory(), COMMAND, this::startCore);
        lifecycle.start();
    }

    @Override
    public void onDisable() {
        if (lifecycle != null) {
            stopCore();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (CoreLifecycle.action(args)) {
            case CoreLifecycle.RELOAD:
                stopCore();
                lifecycle.start();
                sender.sendMessage(lifecycle.reloadReply());
                return true;
            case CoreLifecycle.STATUS:
                lifecycle.status().forEach(sender::sendMessage);
                return true;
            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? new ArrayList<>(CoreLifecycle.ACTIONS) : Collections.<String>emptyList();
    }

    Optional<RadarCore> core() {
        return lifecycle.core();
    }

    private RadarCore startCore(RadarConfig config) {
        Role role = config.behindProxy() ? Role.SERVER_BEHIND_PROXY : Role.SERVER;
        RadarCore core = RadarCore.builder(platform, config, role)
                .floodgate(FloodgateHook.detect(getClass().getClassLoader()))
                .build();
        getServer().getPluginManager().registerEvents(new BukkitListener(core), this);
        core.receiver().ifPresent(this::listenForRewards);
        for (Player player : BukkitCompat.onlinePlayers()) {
            core.online().joined(player.getName());
        }
        core.start();
        return core;
    }

    private void listenForRewards(RewardReceiver receiver) {
        getServer().getMessenger().registerOutgoingPluginChannel(this, RewardMessage.CHANNEL);
        getServer()
                .getMessenger()
                .registerIncomingPluginChannel(
                        this, RewardMessage.CHANNEL, new RewardChannel(this, receiver, scheduler));
    }

    private void stopCore() {
        HandlerList.unregisterAll(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        lifecycle.stop();
    }
}
