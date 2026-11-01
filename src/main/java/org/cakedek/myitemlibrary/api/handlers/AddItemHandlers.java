package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;

import java.io.IOException;
import java.sql.SQLException;

public class AddItemHandlers {
    private final MyItemLibrary plugin;
    private final Api api;

    public AddItemHandlers(MyItemLibrary plugin, Api api) {
        this.plugin = plugin;
        this.api = api;
    }

    public class AddItemHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                api.sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            JsonObject json = api.parseRequestBody(exchange.getRequestBody());
            ItemRequest item = ItemRequest.parse(json);
            String player = ItemRequest.parsePlayer(json);

            try {
                plugin.getDatabase().addItem(item.itemName(), item.itemDisplay(), item.description(), player, true, item.command(), item.used());
                api.sendResponse(exchange, 200, "Item added successfully");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding item via API: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
}
