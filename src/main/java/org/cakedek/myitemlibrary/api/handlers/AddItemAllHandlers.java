package org.cakedek.myitemlibrary.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.CoDatabase;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class AddItemAllHandlers {
    private final MyItemLibrary plugin;
    private final Api api;

    public AddItemAllHandlers(MyItemLibrary plugin, Api api) {
        this.plugin = plugin;
        this.api = api;
    }

    public class AddItemAllHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                api.sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            ItemRequest item = ItemRequest.parse(api.parseRequestBody(exchange.getRequestBody()));

            try {
                CoDatabase database = plugin.getDatabase();
                List<String> allPlayers = database.getAllPlayersEverJoined();
                int addedCount = 0;
                for (String playerName : allPlayers) {
                    database.addItem(item.itemName(), item.itemDisplay(), item.description(), playerName, true, item.command(), item.used());
                    addedCount++;
                }
                api.sendResponse(exchange, 200, "Item added for " + addedCount + " players");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding item for all players via API: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
}
