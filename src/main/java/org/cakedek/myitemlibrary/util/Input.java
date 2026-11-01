package org.cakedek.myitemlibrary.util;

import java.util.regex.Pattern;

public class Input {
    // Java Edition names, optionally with a Floodgate (Bedrock) prefix such as "." or "*".
    private static final Pattern PLAYER_NAME = Pattern.compile("^[.*]?[a-zA-Z0-9_]{1,16}$");

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
}
