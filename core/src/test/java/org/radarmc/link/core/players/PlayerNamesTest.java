package org.radarmc.link.core.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlayerNamesTest {
    @Test
    void voteNickValidation() {
        for (String ok : Arrays.asList("Steve", "a_b_c", ".Bedrock1", "*Gamer", "X")) {
            assertTrue(PlayerNames.isValidVoteNick(ok), ok);
        }
        for (String bad : Arrays.asList("", "a b", "Steve;op", "name_that_is_way_too_long", "..x", "x\n", "%player%")) {
            assertFalse(PlayerNames.isValidVoteNick(bad), bad);
        }
        assertFalse(PlayerNames.isValidVoteNick(null));
    }

    @Test
    void candidatesMapBedrockPrefix() {
        assertEquals(Collections.singletonList("steve"), PlayerNames.candidates("Steve", "."));
        assertEquals(Collections.singletonList(".gamer"), PlayerNames.candidates(".Gamer", "."));
        assertEquals(Arrays.asList(".gamer", "*gamer"), PlayerNames.candidates(".Gamer", "*"));
        assertEquals(Collections.singletonList("*gamer"), PlayerNames.candidates("*Gamer", "bad prefix"));
        assertEquals(Collections.singletonList("steve"), PlayerNames.candidates("Steve", FloodgateHook.NO_PREFIX));
    }

    @Test
    void onlineLookupIsCaseInsensitiveAndExact() {
        OnlinePlayers online = new OnlinePlayers(() -> "*");
        online.joined("Steve");
        online.joined("*Gamer");
        assertEquals(Optional.of("Steve"), online.find("steve"));
        assertEquals(Optional.of("*Gamer"), online.find(".gamer"));
        assertFalse(online.find("Gamer").isPresent());
        online.left("STEVE");
        assertFalse(online.find("Steve").isPresent());
        assertEquals(1, online.size());
    }

    @Test
    void matchesUsesTheSameRules() {
        assertTrue(PlayerNames.matches("steve", "Steve", FloodgateHook.NO_PREFIX));
        assertTrue(PlayerNames.matches(".Gamer", "*gamer", "*"));
        assertFalse(PlayerNames.matches("Gamer", ".Gamer", "."));
    }
}
