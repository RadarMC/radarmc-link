package org.radarmc.link.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.radarmc.link.core.proxy.RewardMessage;
import org.radarmc.link.core.proxy.RewardReceiver;

final class RewardChannel implements PluginMessageListener {
    private final Plugin plugin;
    private final RewardReceiver receiver;
    private final ServerScheduler scheduler;

    RewardChannel(Plugin plugin, RewardReceiver receiver, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.receiver = receiver;
        this.scheduler = scheduler;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!RewardMessage.CHANNEL.equals(channel)) {
            return;
        }
        String name = player.getName();
        scheduler.runGlobal(() -> receiver.receive(name, message).ifPresent(ack -> sendAck(player, ack)));
    }

    private void sendAck(Player player, byte[] ack) {
        scheduler.runForPlayer(player, () -> {
            if (player.isOnline()) {
                player.sendPluginMessage(plugin, RewardMessage.CHANNEL, ack);
            }
        });
    }
}
