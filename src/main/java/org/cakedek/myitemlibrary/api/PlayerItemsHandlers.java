package org.cakedek.myitemlibrary.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.Api;
import org.cakedek.myitemlibrary.CoDatabase;
import org.cakedek.myitemlibrary.ItemData;
import org.cakedek.myitemlibrary.MyItemLibrary;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

public class PlayerItemsHandlers {
    private final MyItemLibrary plugin;
    private final CoDatabase database;
    private final Api api;
    private final Gson gson;

    public PlayerItemsHandlers(MyItemLibrary plugin, CoDatabase database, Api api, Gson gson) {
        this.plugin = plugin;
        this.database = database;
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

            if (api.validateApiKey(exchange)) {
                api.sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] pathParts = path.split("/");
            if (pathParts.length != 3) {
                api.sendResponse(exchange, 400, "Bad Request");
                return;
            }

            String playerName = URLDecoder.decode(pathParts[2], StandardCharsets.UTF_8);

            try {
                List<ItemData> items = database.getItemsByPlayer(playerName);
                if (items.isEmpty()) {
                    api.sendResponse(exchange, 404, "No items found for player: " + playerName);
                } else {
                    String response = gson.toJson(items);
                    api.sendResponse(exchange, 200, response);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching items for player: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
}
