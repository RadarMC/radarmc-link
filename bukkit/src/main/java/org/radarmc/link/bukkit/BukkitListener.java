package org.radarmc.link.bukkit;

import java.net.InetSocketAddress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.radarmc.link.core.RadarCore;

final class BukkitListener implements Listener {
    private final RadarCore core;

    BukkitListener(RadarCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        InetSocketAddress address = player.getAddress();
        core.onJoin(player.getUniqueId(), player.getName(), address == null ? null : address.getAddress());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        core.onQuit(event.getPlayer().getName());
    }
}
