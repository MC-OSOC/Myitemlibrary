package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.ItemData;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.cakedek.myitemlibrary.util.Input.isValidPlayerName;

public class PlayerItemsHandlers {
    private final MyItemLibrary plugin;
    private final Api api;
    private final Gson gson;

    public PlayerItemsHandlers(MyItemLibrary plugin, Api api, Gson gson) {
        this.plugin = plugin;
        this.api = api;
        this.gson = gson;
    }

    public class PlayerItemsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                api.sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            // getPath() is already URL-decoded.
            String[] pathParts = exchange.getRequestURI().getPath().split("/");
            if (pathParts.length != 3) {
                api.sendResponse(exchange, 400, "Bad Request");
                return;
            }

            String playerName = pathParts[2];
            if (!isValidPlayerName(playerName)) {
                api.sendResponse(exchange, 400, "Invalid player name");
                return;
            }

            try {
                List<ItemData> items = plugin.getDatabase().getItemsByPlayer(playerName);
                if (items.isEmpty()) {
                    api.sendResponse(exchange, 404, "No items found for player: " + playerName);
                } else {
                    api.sendResponse(exchange, 200, gson.toJson(items));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching items for player: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
}
