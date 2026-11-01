package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.ItemData;

import java.io.IOException;
import java.sql.SQLException;

public class ItemOperationsHandlers {
    private final MyItemLibrary plugin;
    private final Api api;
    private final Gson gson;

    public ItemOperationsHandlers(MyItemLibrary plugin, Api api, Gson gson) {
        this.plugin = plugin;
        this.api = api;
        this.gson = gson;
    }

    public class ItemOperationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String[] pathParts = exchange.getRequestURI().getPath().split("/");
            if (pathParts.length != 3) {
                api.sendResponse(exchange, 400, "Bad Request");
                return;
            }

            int itemId;
            try {
                itemId = Integer.parseInt(pathParts[2]);
            } catch (NumberFormatException e) {
                itemId = -1;
            }
            if (itemId <= 0) {
                api.sendResponse(exchange, 400, "Invalid Item ID: must be a positive integer");
                return;
            }

            switch (exchange.getRequestMethod()) {
                case "GET":
                    handleGetItem(exchange, itemId);
                    break;
                case "DELETE":
                    handleDeleteItem(exchange, itemId);
                    break;
                default:
                    api.sendResponse(exchange, 405, "Method Not Allowed");
            }
        }

        private void handleGetItem(HttpExchange exchange, int itemId) throws IOException {
            try {
                ItemData item = plugin.getDatabase().getItem(itemId);
                if (item != null) {
                    api.sendResponse(exchange, 200, gson.toJson(item));
                } else {
                    api.sendResponse(exchange, 404, "Item not found");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching item: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }

        private void handleDeleteItem(HttpExchange exchange, int itemId) throws IOException {
            try {
                if (plugin.getDatabase().deleteItem(itemId)) {
                    api.sendResponse(exchange, 200, "Item deleted successfully");
                } else {
                    api.sendResponse(exchange, 404, "Item not found");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error deleting item: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
}
