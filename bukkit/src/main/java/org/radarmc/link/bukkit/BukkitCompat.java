package org.radarmc.link.bukkit;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class BukkitCompat {
    private static final Method GET_ONLINE_PLAYERS = onlinePlayersMethod();

    private BukkitCompat() {}

    @SuppressWarnings("unchecked")
    static Collection<? extends Player> onlinePlayers() {
        try {
            Object result = GET_ONLINE_PLAYERS.invoke(null);
            if (result instanceof Player[]) {
                return Arrays.asList((Player[]) result);
            }
            return result == null ? Collections.<Player>emptyList() : (Collection<? extends Player>) result;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Collections.emptyList();
        }
    }

    static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static Method onlinePlayersMethod() {
        try {
            return Bukkit.class.getMethod("getOnlinePlayers");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Bukkit.getOnlinePlayers() is missing", e);
        }
    }
}
