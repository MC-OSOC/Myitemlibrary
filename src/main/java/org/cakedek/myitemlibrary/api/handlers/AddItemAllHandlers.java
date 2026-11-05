package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.CoDatabase;

import java.io.IOException;
import java.sql.SQLException;

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

            JsonObject json = api.parseRequestBody(exchange.getRequestBody());
            ItemRequest item = ItemRequest.parse(json);
            String orderId = ItemRequest.parseOrderId(json);

            try {
                CoDatabase database = api.database();
                // Everyone who ever had an item, with their UUID where it is known.
                CoDatabase.AddResult result = database.addItems(item.itemName(), item.itemDisplay(), item.description(),
                        item.command(), item.used(), database.getAllOwners(), orderId, "add-item-all");
                api.sendAddResult(exchange, result, orderId, "Item added for " + result.count() + " players");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding item for all players via API: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
}
