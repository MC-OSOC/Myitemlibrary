package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.CoDatabase;

import java.io.IOException;
import java.sql.SQLException;

public class AddItemOnlineHandlers {
    private final MyItemLibrary plugin;
    private final Api api;

    public AddItemOnlineHandlers(MyItemLibrary plugin, Api api) {
        this.plugin = plugin;
        this.api = api;
    }

    public class AddItemOnlineHandler implements HttpHandler {
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
                // The online list is kept up to date by join/quit events, so no main-thread call is needed.
                CoDatabase.AddResult result = api.database().addItems(item.itemName(), item.itemDisplay(),
                        item.description(), item.command(), item.used(), plugin.getOnlinePlayers().all(),
                        orderId, "add-item-online");
                api.sendAddResult(exchange, result, orderId, "Item added for " + result.count() + " online players");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding item for online players via API: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
}
