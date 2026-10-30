package org.radarmc.link.core.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloodgateHookTest {
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7c3");

    public static final class ModernApi {
        public boolean isFloodgatePlayer(UUID uuid) {
            return BEDROCK.equals(uuid);
        }

        public String getPlayerPrefix() {
            return ".";
        }
    }

    public static final class LegacyApi {
        private LegacyApi() {}

        public static boolean isBedrockPlayer(UUID uuid) {
            return BEDROCK.equals(uuid);
        }
    }

    @Test
    void modernApi() throws Exception {
        FloodgateHook hook = FloodgateHook.of(
                new ModernApi(),
                ModernApi.class.getMethod("isFloodgatePlayer", UUID.class),
                ModernApi.class.getMethod("getPlayerPrefix"));
        assertTrue(hook.available());
        assertTrue(hook.isBedrock(BEDROCK));
        assertFalse(hook.isBedrock(UUID.randomUUID()));
        assertEquals(".", hook.prefix());
    }

    @Test
    void legacyStaticApi() throws Exception {
        FloodgateHook hook =
                FloodgateHook.of(LegacyApi.class, LegacyApi.class.getMethod("isBedrockPlayer", UUID.class), null);
        assertTrue(hook.available());
        assertTrue(hook.isBedrock(BEDROCK));
        assertEquals(FloodgateHook.NO_PREFIX, hook.prefix());
    }

    @Test
    void absentWithoutFloodgate() {
        FloodgateHook hook = FloodgateHook.detect(getClass().getClassLoader());
        assertFalse(hook.available());
        assertFalse(hook.isBedrock(BEDROCK));
        assertEquals(FloodgateHook.NO_PREFIX, hook.prefix());
    }
}
