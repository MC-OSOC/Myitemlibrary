package org.cakedek.myitemlibrary.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.FileConfiguration;
import com.google.gson.*;
import org.cakedek.myitemlibrary.config.ApiConfig;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.handlers.*;
import org.cakedek.myitemlibrary.util.RateLimiter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Api {
    private final MyItemLibrary plugin;
    private HttpServer server;
    private final Gson gson;
    private String apiKey;
    private final RateLimiter rateLimiter;
    private final boolean dosProtectionEnabled;
    private final int maxRequestSizeBytes;

    private final PlayerItemsHandlers playerItemsHandlers;
    private final AddItemHandlers addItemHandlers;
    private final AddItemAllHandlers addItemAllHandlers;
    private final AddItemOnlineHandlers addItemOnlineHandlers;
    private final GetShowAllItemsHandlers getShowAllItemsHandlers;
    private final ItemOperationsHandlers itemOperationsHandlers;

    public Api(MyItemLibrary plugin) {
        this.plugin = plugin;
        CoDatabase database = plugin.getDatabase();
        this.gson = new GsonBuilder().disableHtmlEscaping().create();

        this.dosProtectionEnabled = plugin.isDosProtectionEnabled();
        this.maxRequestSizeBytes = plugin.getMaxRequestSizeBytes();
        this.rateLimiter = new RateLimiter(plugin.getMaxRequestsPerMinute(), plugin.getRequestTimeWindowMs());

        // Initialize all handlers
        this.playerItemsHandlers = new PlayerItemsHandlers(plugin, database, this, gson);
        this.addItemHandlers = new AddItemHandlers(plugin, database, this);
        this.addItemAllHandlers = new AddItemAllHandlers(plugin, database, this);
        this.addItemOnlineHandlers = new AddItemOnlineHandlers(plugin, database, this);
        this.getShowAllItemsHandlers = new GetShowAllItemsHandlers(plugin, database, this, gson);
        this.itemOperationsHandlers = new ItemOperationsHandlers(plugin, database, this, gson);
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

            createProtectedContext("/items/", playerItemsHandlers.new PlayerItemsHandler());
            createProtectedContext("/add-item", addItemHandlers.new AddItemHandler());
            createProtectedContext("/add-item-all", addItemAllHandlers.new AddItemAllHandler());
            createProtectedContext("/add-item-online", addItemOnlineHandlers.new AddItemOnlineHandler());
            createProtectedContext("/items", getShowAllItemsHandlers.new GetShowAllItemsHandler());
            createProtectedContext("/item/", itemOperationsHandlers.new ItemOperationsHandler());

            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("API server started on " + host + ":" + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
    }

    private void createProtectedContext(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            try {
                plugin.getLogger().info("Received request for path: " + path);

                if (dosProtectionEnabled) {
                    String remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
                    if (!rateLimiter.allowRequest(remoteAddress)) {
                        sendResponse(exchange, 429, "Too Many Requests");
                        return;
                    }

                    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        int contentLength = Integer.parseInt(exchange.getRequestHeaders().getFirst("Content-length"));
                        if (contentLength > maxRequestSizeBytes) {
                            sendResponse(exchange, 413, "Request Entity Too Large");
                            return;
                        }
                    }
                }

                if (validateApiKey(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                handler.handle(exchange);
            } catch (Exception e) {
                plugin.getLogger().severe("Error handling request for path " + path + ": " + e.getMessage());
                e.printStackTrace();
                try {
                    sendResponse(exchange, 500, "Internal Server Error");
                } catch (IOException ioe) {
                    plugin.getLogger().severe("Failed to send error response: " + ioe.getMessage());
                }
            } finally {
                exchange.close();
            }
        });
    }



    public void stopServer() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("API server stopped");
        }
    }

    public boolean validateApiKey(HttpExchange exchange) {
        String requestApiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        return !apiKey.equals(requestApiKey);
    }

    public JsonObject parseRequestBody(InputStream requestBody) {
        InputStreamReader reader = new InputStreamReader(requestBody, StandardCharsets.UTF_8);
        return gson.fromJson(reader, JsonObject.class);
    }

    public void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

}