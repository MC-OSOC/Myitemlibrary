package org.cakedek.myitemlibrary;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.FileConfiguration;
import com.google.gson.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

public class ApiHandler {
    private final My_item_library plugin;
    private final CoDatabase database;
    private HttpServer server;
    private final Gson gson;
    private String apiKey;

    public ApiHandler(My_item_library plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
    }

    public void startServer() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("c-api.c-api-enable", false)) {
            return;
        }

        String host = config.getString("c-api.c-api-host", "0.0.0.0");
        int port = config.getInt("c-api.c-api-port", 1558);
        this.apiKey = config.getString("c-api.c-api-key", "");

        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/add-item", new AddItemHandler());
            server.createContext("/items", new GetAllItemsHandler());
            server.createContext("/item/", new ItemOperationsHandler());
            server.createContext("/items/", new PlayerItemsHandler());

            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("API server started on " + host + ":" + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
    }

    public void stopServer() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("API server stopped");
        }
    }

    private class AddItemHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            if (validateApiKey(exchange)) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                JsonObject jsonObject = parseRequestBody(exchange.getRequestBody());
                String itemName = jsonObject.get("item_name").getAsString();
                String itemDisplay = jsonObject.get("item_display").getAsString();
                String description = jsonObject.get("description").getAsString();
                String player = jsonObject.get("player").getAsString();
                String command = jsonObject.get("command").getAsString();
                int used = jsonObject.get("used").getAsInt();

                database.addItem(itemName, itemDisplay, description, player, true, command, used);
                sendResponse(exchange, 200, "Item added successfully");
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing API request: " + e.getMessage());
                sendResponse(exchange, 400, "Bad Request");
            }
        }
    }

    private class GetAllItemsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            if (validateApiKey(exchange)) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                List<ItemData> items = database.getAllItems();
                String response = gson.toJson(items);
                sendResponse(exchange, 200, response);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching items: " + e.getMessage());
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    private class ItemOperationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (validateApiKey(exchange)) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] pathParts = path.split("/");
            if (pathParts.length != 3) {
                sendResponse(exchange, 400, "Bad Request");
                return;
            }

            int itemId;
            try {
                itemId = Integer.parseInt(pathParts[2]);
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "Invalid Item ID");
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
                    sendResponse(exchange, 405, "Method Not Allowed");
            }
        }

        private void handleGetItem(HttpExchange exchange, int itemId) throws IOException {
            try {
                ItemData item = database.getItem(itemId);
                if (item != null) {
                    String response = gson.toJson(item);
                    sendResponse(exchange, 200, response);
                } else {
                    sendResponse(exchange, 404, "Item not found");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching item: " + e.getMessage());
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }

        private void handleDeleteItem(HttpExchange exchange, int itemId) throws IOException {
            try {
                boolean deleted = database.deleteItem(itemId);
                if (deleted) {
                    sendResponse(exchange, 200, "Item deleted successfully");
                } else {
                    sendResponse(exchange, 404, "Item not found");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error deleting item: " + e.getMessage());
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    private class PlayerItemsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            if (validateApiKey(exchange)) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] pathParts = path.split("/");
            if (pathParts.length != 3) {
                sendResponse(exchange, 400, "Bad Request");
                return;
            }

            String playerName = URLDecoder.decode(pathParts[2], StandardCharsets.UTF_8.toString());

            try {
                List<ItemData> items = database.getItemsByPlayer(playerName);
                if (items.isEmpty()) {
                    sendResponse(exchange, 404, "No items found for player: " + playerName);
                } else {
                    String response = gson.toJson(items);
                    sendResponse(exchange, 200, response);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching items for player: " + e.getMessage());
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    private boolean validateApiKey(HttpExchange exchange) {
        String requestApiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        return !apiKey.equals(requestApiKey);
    }

    private JsonObject parseRequestBody(InputStream requestBody) throws IOException {
        InputStreamReader reader = new InputStreamReader(requestBody, StandardCharsets.UTF_8);
        return gson.fromJson(reader, JsonObject.class);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}