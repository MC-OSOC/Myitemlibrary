package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.JsonElement;
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

    /** Optional "player_uuid": null if absent or JSON null, otherwise it must be a valid UUID. */
    static String parsePlayerUuid(JsonObject json) {
        String uuid = optionalString(json, "player_uuid");
        if (uuid == null) {
            return null;
        }
        uuid = uuid.trim();
        if (!Input.isValidUuid(uuid)) {
            throw new Api.BadRequestException("Invalid player_uuid (expected xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)");
        }
        return Input.normalizeUuid(uuid);
    }

    /** Optional "order_id": null if absent or JSON null, otherwise 1-64 characters of [A-Za-z0-9._:-]. */
    static String parseOrderId(JsonObject json) {
        String orderId = optionalString(json, "order_id");
        if (orderId == null) {
            return null;
        }
        if (!Input.isValidOrderId(orderId)) {
            throw new Api.BadRequestException("Invalid order_id (1-64 characters of A-Z a-z 0-9 . _ : -)");
        }
        return orderId;
    }

    private static String optionalString(JsonObject json, String field) {
        JsonElement e = json.get(field);
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new Api.BadRequestException("Field '" + field + "' must be a string");
        }
        return e.getAsString();
    }
}
