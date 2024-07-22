package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.database.ItemData;
import org.cakedek.myitemlibrary.MyItemLibrary;

import java.io.IOException;
import java.sql.SQLException;

public class ItemOperationsHandlers {
    private final MyItemLibrary plugin;
    private final CoDatabase database;
    private final Api api;
    private final Gson gson;

    public ItemOperationsHandlers(MyItemLibrary plugin, CoDatabase database, Api api, Gson gson) {
        this.plugin = plugin;
        this.database = database;
        this.api = api;
        this.gson = gson;
    }

    public class ItemOperationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
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

            int itemId;
            try {
                itemId = Integer.parseInt(pathParts[2]);
                if (itemId <= 0) {
                    throw new IllegalArgumentException("Invalid item ID");
                }
            } catch (NumberFormatException e) {
                api.sendResponse(exchange, 400, "Invalid Item ID: must be a positive integer");
                return;
            } catch (IllegalArgumentException e) {
                api.sendResponse(exchange, 400, e.getMessage());
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
                ItemData item = database.getItem(itemId);
                if (item != null) {
                    String response = gson.toJson(item);
                    api.sendResponse(exchange, 200, response);
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
                boolean deleted = database.deleteItem(itemId);
                if (deleted) {
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
