package org.cakedek.myitemlibrary.util;

import java.util.Locale;
import java.util.regex.Pattern;

public class Input {
    // Java Edition names, optionally with a Floodgate (Bedrock) prefix such as "." or "*".
    private static final Pattern PLAYER_NAME = Pattern.compile("^[.*]?[a-zA-Z0-9_]{1,16}$");
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern ORDER_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    public static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        input = input.replaceAll("(?i)<(?!player>)[^>]*>", "|lt|$0|gt|");
        input = input.replace("<", "|lt|").replace(">", "|gt|");
        input = input.replace("|lt|player|gt|", "<player>");
        return input;
    }

    /** True if the name is safe to store and to insert into a console command (no spaces, selectors, etc). */
    public static boolean isValidPlayerName(String playerName) {
        return playerName != null && PLAYER_NAME.matcher(playerName).matches();
    }

    /** True for a UUID in the standard 8-4-4-4-12 hex form. */
    public static boolean isValidUuid(String uuid) {
        return uuid != null && UUID_PATTERN.matcher(uuid).matches();
    }

    /** Canonical (lower-case) form of a UUID that passed {@link #isValidUuid}; this is how UUIDs are stored. */
    public static String normalizeUuid(String uuid) {
        return uuid.toLowerCase(Locale.ROOT);
    }

    /** Webshop order id: 1-64 characters of A-Z, a-z, 0-9, '.', '_', ':' and '-'. */
    public static boolean isValidOrderId(String orderId) {
        return orderId != null && ORDER_ID.matcher(orderId).matches();
    }
}
