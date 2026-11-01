package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.JsonObject;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.util.Input;

import static org.cakedek.myitemlibrary.util.Input.sanitizeInput;

/** Validated body of the add-item endpoints. Shared so all three endpoints apply the same rules. */
record ItemRequest(String itemName, String itemDisplay, String description, String command, int used) {
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_TEXT_LENGTH = 4096;

    static ItemRequest parse(JsonObject json) {
        String itemName = sanitizeInput(Api.requireString(json, "item_name"));
        String itemDisplay = sanitizeInput(Api.requireString(json, "item_display"));
        String description = sanitizeInput(Api.requireString(json, "description"));
        String command = sanitizeInput(Api.requireString(json, "command"));
        int used = Math.max(0, Api.requireInt(json, "used"));

        if (itemName.isEmpty() || itemDisplay.isEmpty() || command.trim().isEmpty()) {
            throw new Api.BadRequestException("item_name, item_display and command must be non-empty");
        }
        if (itemName.length() > MAX_NAME_LENGTH || itemDisplay.length() > MAX_NAME_LENGTH) {
            throw new Api.BadRequestException("Item name or display name is too long (max " + MAX_NAME_LENGTH + " characters)");
        }
        if (description.length() > MAX_TEXT_LENGTH || command.length() > MAX_TEXT_LENGTH) {
            throw new Api.BadRequestException("Description or command is too long (max " + MAX_TEXT_LENGTH + " characters)");
        }
        return new ItemRequest(itemName, itemDisplay, description, command, used);
    }

    static String parsePlayer(JsonObject json) {
        String player = Api.requireString(json, "player").trim();
        if (!Input.isValidPlayerName(player)) {
            throw new Api.BadRequestException("Invalid player name");
        }
        return player;
    }
}
