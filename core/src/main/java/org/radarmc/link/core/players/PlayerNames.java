package org.radarmc.link.core.players;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PlayerNames {
    private static final Pattern VOTE_NICK = Pattern.compile("^[.*]?[A-Za-z0-9_]{1,16}$");
    private static final Pattern FLOODGATE_PREFIX = Pattern.compile("^[^A-Za-z0-9_\\s]{1,4}$");

    private PlayerNames() {}

    public static boolean isValidVoteNick(String nick) {
        return nick != null && VOTE_NICK.matcher(nick).matches();
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static List<String> candidates(String nick, String floodgatePrefix) {
        List<String> keys = new ArrayList<>();
        keys.add(key(nick));
        if (!FLOODGATE_PREFIX.matcher(floodgatePrefix).matches() || !isBedrockNick(nick)) {
            return keys;
        }
        String withPrefix = key(floodgatePrefix + nick.substring(1));
        if (!keys.contains(withPrefix)) {
            keys.add(withPrefix);
        }
        return keys;
    }

    public static boolean matches(String nick, String onlineName, String floodgatePrefix) {
        return candidates(nick, floodgatePrefix).contains(key(onlineName));
    }

    private static boolean isBedrockNick(String nick) {
        return nick.startsWith(".") || nick.startsWith("*");
    }
}
