package org.cakedek.myitemlibrary.util;

import java.util.regex.Pattern;

public class InputValidator {

    private static final int MAX_STRING_LENGTH = 255;
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Pattern COMMAND_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\s]+$");

    public static String validateString(String input, String fieldName) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        if (input.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException(fieldName + " is too long (max " + MAX_STRING_LENGTH + " characters)");
        }
        return input.trim();
    }

    public static String validatePlayerName(String playerName) {
        String validatedName = validateString(playerName, "Player name");
        if (!ALPHANUMERIC_PATTERN.matcher(validatedName).matches()) {
            throw new IllegalArgumentException("Player name contains invalid characters");
        }
        return validatedName;
    }

    public static String validateCommand(String command) {
        String validatedCommand = validateString(command, "Command");
        if (!COMMAND_PATTERN.matcher(validatedCommand).matches()) {
            throw new IllegalArgumentException("Command contains invalid characters");
        }
        return validatedCommand;
    }

    public static int validateInteger(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max);
        }
        return value;
    }

    public static boolean validateBoolean(Boolean value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return value;
    }
}