package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.MyItemLibrary;

import java.io.IOException;
import java.util.List;

import static org.cakedek.myitemlibrary.util.Input.sanitizeInput;

public class AddItemAllHandlers {
    private final MyItemLibrary plugin;
    private final CoDatabase database;
    private final Api api;

    public AddItemAllHandlers(MyItemLibrary plugin, CoDatabase database, Api api) {
        this.plugin = plugin;
        this.database = database;
        this.api = api;
    }

    public class AddItemAllHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                api.sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            if (api.validateApiKey(exchange)) {
                api.sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                JsonObject jsonObject = api.parseRequestBody(exchange.getRequestBody());
                String itemName = sanitizeInput(jsonObject.get("item_name").getAsString());
                String itemDisplay = sanitizeInput(jsonObject.get("item_display").getAsString());
                String description = sanitizeInput(jsonObject.get("description").getAsString());
                String command = sanitizeInput(jsonObject.get("command").getAsString());
                int used = validateInteger(jsonObject.get("used").getAsInt(), 0, Integer.MAX_VALUE);

                if (itemName.isEmpty() || itemDisplay.isEmpty() || description.isEmpty() || command.isEmpty()) {
                    throw new IllegalArgumentException("All fields must be non-empty");
                }

                if (itemName.length() > 255 || itemDisplay.length() > 255) {
                    throw new IllegalArgumentException("Item name or display name is too long (max 255 characters)");
                }

                List<String> allPlayers = database.getAllPlayersEverJoined();
                int addedCount = 0;
                for (String playerName : allPlayers) {
                    database.addItem(itemName, itemDisplay, description, playerName, true, command, used);
                    addedCount++;
                }

                api.sendResponse(exchange, 200, "Item added for " + addedCount + " players");
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid input in API request: " + e.getMessage());
                api.sendResponse(exchange, 400, "Bad Request: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing API request: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
        private int validateInteger(int value, int min, int max) {
            return Math.max(min, Math.min(value, max));
        }
    }
}