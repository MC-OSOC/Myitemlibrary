package org.cakedek.myitemlibrary.api;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.cakedek.myitemlibrary.Api;
import org.cakedek.myitemlibrary.CoDatabase;
import org.cakedek.myitemlibrary.MyItemLibrary;

import java.io.IOException;

public class AddItemOnlineHandlers {
    private final MyItemLibrary plugin;
    private final CoDatabase database;
    private final Api api;

    public AddItemOnlineHandlers(MyItemLibrary plugin, CoDatabase database, Api api) {
        this.plugin = plugin;
        this.database = database;
        this.api = api;
    }

    public class AddItemOnlineHandler implements HttpHandler {
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
                String itemName = jsonObject.get("item_name").getAsString();
                String itemDisplay = jsonObject.get("item_display").getAsString();
                String description = jsonObject.get("description").getAsString();
                String command = jsonObject.get("command").getAsString();
                int used = jsonObject.get("used").getAsInt();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    database.addItem(itemName, itemDisplay, description, player.getName(), true, command, used);
                }

                api.sendResponse(exchange, 200, "Item added for all online players");
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing API request: " + e.getMessage());
                api.sendResponse(exchange, 400, "Bad Request");
            }
        }
    }

}
