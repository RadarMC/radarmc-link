package org.radarmc.link.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.radarmc.link.core.BuildInfo;
import org.radarmc.link.core.proxy.RewardMessage;

class VelocityDescriptorTest {
    @Test
    void pluginDescriptorIsGenerated() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/velocity-plugin.json")) {
            assertNotNull(in);
            JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals("radarlink", json.get("id").getAsString());
            assertEquals("RadarLink", json.get("name").getAsString());
            assertEquals(BuildInfo.VERSION, json.get("version").getAsString());
            assertEquals(RadarLinkVelocity.class.getName(), json.get("main").getAsString());
            assertEquals(
                    "floodgate",
                    json.getAsJsonArray("dependencies")
                            .get(0)
                            .getAsJsonObject()
                            .get("id")
                            .getAsString());
        }
    }

    @Test
    void channelMatchesTheServerPlugin() {
        assertEquals(RewardMessage.CHANNEL, RadarLinkVelocity.CHANNEL.getId());
    }
}
