package org.cakedek.myitemlibrary.util;

public class Input {
    public static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        input = input.replaceAll("(?i)<(?!player>)[^>]*>", "|lt|$0|gt|");
        input = input.replace("<", "|lt|").replace(">", "|gt|");
        input = input.replace("|lt|player|gt|", "<player>");
        return input;
    }
    public static boolean isValidPlayerName(String playerName) {
        return playerName.matches("^[a-zA-Z0-9_]{1,16}$");
    }
}
