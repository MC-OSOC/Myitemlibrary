package org.cakedek.myitemlibrary;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import com.google.gson.*;
import org.bukkit.entity.Player;
import org.cakedek.myitemlibrary.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

public class Api {
    private final MyItemLibrary plugin;
    private final CoDatabase database;
    private HttpServer server;
    private final Gson gson;
    private String apiKey;

    private PlayerItemsHandlers playerItemsHandlers;
    private AddItemHandlers addItemHandlers;
    private AddItemAllHandlers addItemAllHandlers;
    private AddItemOnlineHandlers addItemOnlineHandlers;
    private GetShowAllItemsHandlers getShowAllItemsHandlers;
    private ItemOperationsHandlers itemOperationsHandlers;

    public Api(MyItemLibrary plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
        this.gson = new GsonBuilder().disableHtmlEscaping().create();

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
            server.createContext("/items/", playerItemsHandlers.new PlayerItemsHandler());
            server.createContext("/add-item", addItemHandlers.new AddItemHandler());
            server.createContext("/add-item-all", addItemAllHandlers.new AddItemAllHandler());
            server.createContext("/add-item-online", addItemOnlineHandlers.new AddItemOnlineHandler());
            server.createContext("/items", getShowAllItemsHandlers.new GetShowAllItemsHandler());
            server.createContext("/item/", itemOperationsHandlers.new ItemOperationsHandler());
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

    public boolean validateApiKey(HttpExchange exchange) {
        String requestApiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        return !apiKey.equals(requestApiKey);
    }

    public JsonObject parseRequestBody(InputStream requestBody) throws IOException {
        InputStreamReader reader = new InputStreamReader(requestBody, StandardCharsets.UTF_8);
        return gson.fromJson(reader, JsonObject.class);
    }

    public void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}