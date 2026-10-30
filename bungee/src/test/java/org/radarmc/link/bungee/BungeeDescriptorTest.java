package org.radarmc.link.bungee;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.radarmc.link.core.BuildInfo;

class BungeeDescriptorTest {
    @Test
    void descriptorHasVersionAndMain() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/bungee.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("version: \"" + BuildInfo.VERSION + "\""), yml);
            assertTrue(yml.contains("main: " + RadarLinkBungee.class.getName()), yml);
            assertTrue(yml.contains("softDepends: [floodgate]"), yml);
            assertTrue(yml.contains("name: RadarLink\n"), yml);
        }
    }
}
