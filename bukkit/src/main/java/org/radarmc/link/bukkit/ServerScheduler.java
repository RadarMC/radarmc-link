package org.radarmc.link.bukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

interface ServerScheduler {
    void runGlobal(Runnable task);

    void runForPlayer(Player player, Runnable task);

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static ServerScheduler create(Plugin plugin) {
        return isFolia() ? FoliaScheduler.create(plugin) : new BukkitScheduler(plugin);
    }

    final class BukkitScheduler implements ServerScheduler {
        private final Plugin plugin;

        BukkitScheduler(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void runGlobal(Runnable task) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }

        @Override
        public void runForPlayer(Player player, Runnable task) {
            runGlobal(task);
        }
    }

    final class FoliaScheduler implements ServerScheduler {
        private static final String PACKAGE = "io.papermc.paper.threadedregions.scheduler.";
        private static final long ENTITY_TASK_DELAY_TICKS = 1L;

        private final Plugin plugin;
        private final Object globalScheduler;
        private final Method globalExecute;
        private final Method entityScheduler;
        private final Method entityExecute;

        private FoliaScheduler(
                Plugin plugin,
                Object globalScheduler,
                Method globalExecute,
                Method entityScheduler,
                Method entityExecute) {
            this.plugin = plugin;
            this.globalScheduler = globalScheduler;
            this.globalExecute = globalExecute;
            this.entityScheduler = entityScheduler;
            this.entityExecute = entityExecute;
        }

        static FoliaScheduler create(Plugin plugin) {
            try {
                Object global =
                        Server.class.getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
                Method globalExecute = Class.forName(PACKAGE + "GlobalRegionScheduler")
                        .getMethod("execute", Plugin.class, Runnable.class);
                Method entityScheduler = Entity.class.getMethod("getScheduler");
                Method entityExecute = Class.forName(PACKAGE + "EntityScheduler")
                        .getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
                return new FoliaScheduler(plugin, global, globalExecute, entityScheduler, entityExecute);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Folia detected, but its scheduler API was not found", e);
            }
        }

        @Override
        public void runGlobal(Runnable task) {
            invoke(globalExecute, globalScheduler, plugin, task);
        }

        @Override
        public void runForPlayer(Player player, Runnable task) {
            try {
                Object scheduler = entityScheduler.invoke(player);
                invoke(entityExecute, scheduler, plugin, task, null, ENTITY_TASK_DELAY_TICKS);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Folia entity scheduler failed", e);
            }
        }

        private static void invoke(Method method, Object target, Object... args) {
            try {
                method.invoke(target, args);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Folia scheduler failed", e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new IllegalStateException("Folia scheduler failed", cause);
            }
        }
    }
}
