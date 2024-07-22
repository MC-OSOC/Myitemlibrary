package org.cakedek.myitemlibrary.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.JsonObject;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.MyItemLibrary;

import java.io.IOException;

import static org.cakedek.myitemlibrary.util.Input.sanitizeInput;

public class AddItemHandlers {

    private final MyItemLibrary plugin;
    private final CoDatabase database;
    private final Api api;

    public AddItemHandlers(MyItemLibrary plugin, CoDatabase database, Api api) {
        this.plugin = plugin;
        this.database = database;
        this.api = api;
    }

    public class AddItemHandler implements HttpHandler {
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
                String player = sanitizeInput(jsonObject.get("player").getAsString());
                String command = sanitizeInput(jsonObject.get("command").getAsString());
                int used = validateInteger(jsonObject.get("used").getAsInt(), 0, Integer.MAX_VALUE);

                if (itemName.length() > 255 || itemDisplay.length() > 255 || player.length() > 255) {
                    throw new IllegalArgumentException("Input string too long");
                }

                database.addItem(itemName, itemDisplay, description, player, true, command, used);
                api.sendResponse(exchange, 200, "Item added successfully");
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing API request: " + e.getMessage());
                api.sendResponse(exchange, 400, "Bad Request: " + e.getMessage());
            }
        }
        private int validateInteger(int value, int min, int max) {
            return Math.max(min, Math.min(value, max));
        }
    }
}