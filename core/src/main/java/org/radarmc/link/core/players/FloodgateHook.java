package org.radarmc.link.core.players;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

public final class FloodgateHook {
    public static final String NO_PREFIX = "";
    private static final String API_CLASS = "org.geysermc.floodgate.api.FloodgateApi";
    private static final String LEGACY_API_CLASS = "org.geysermc.floodgate.FloodgateAPI";
    private static final FloodgateHook ABSENT = new FloodgateHook(null, null, null);

    private final Object api;
    private final Method isFloodgatePlayer;
    private final Method getPlayerPrefix;

    private FloodgateHook(Object api, Method isFloodgatePlayer, Method getPlayerPrefix) {
        this.api = api;
        this.isFloodgatePlayer = isFloodgatePlayer;
        this.getPlayerPrefix = getPlayerPrefix;
    }

    public static FloodgateHook absent() {
        return ABSENT;
    }

    public static FloodgateHook detect(ClassLoader loader) {
        FloodgateHook modern = detectModern(loader);
        return modern.available() ? modern : detectLegacy(loader);
    }

    static FloodgateHook of(Object api, Method isFloodgatePlayer, Method getPlayerPrefix) {
        return new FloodgateHook(api, isFloodgatePlayer, getPlayerPrefix);
    }

    private static FloodgateHook detectModern(ClassLoader loader) {
        try {
            Class<?> type = Class.forName(API_CLASS, true, loader);
            Object api = type.getMethod("getInstance").invoke(null);
            if (api == null) {
                return ABSENT;
            }
            return of(api, type.getMethod("isFloodgatePlayer", UUID.class), type.getMethod("getPlayerPrefix"));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return ABSENT;
        }
    }

    private static FloodgateHook detectLegacy(ClassLoader loader) {
        try {
            Class<?> type = Class.forName(LEGACY_API_CLASS, true, loader);
            Method isBedrockPlayer = type.getMethod("isBedrockPlayer", UUID.class);
            if (!Modifier.isStatic(isBedrockPlayer.getModifiers())) {
                return ABSENT;
            }
            return of(type, isBedrockPlayer, null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return ABSENT;
        }
    }

    public boolean available() {
        return api != null && isFloodgatePlayer != null;
    }

    public boolean isBedrock(UUID uuid) {
        if (!available()) {
            return false;
        }
        try {
            Object target = Modifier.isStatic(isFloodgatePlayer.getModifiers()) ? null : api;
            return Boolean.TRUE.equals(isFloodgatePlayer.invoke(target, uuid));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public String prefix() {
        if (!available() || getPlayerPrefix == null) {
            return NO_PREFIX;
        }
        try {
            Object prefix = getPlayerPrefix.invoke(api);
            return prefix instanceof String ? (String) prefix : NO_PREFIX;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return NO_PREFIX;
        }
    }
}
