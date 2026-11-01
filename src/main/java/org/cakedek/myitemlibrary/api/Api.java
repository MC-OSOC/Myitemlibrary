package org.cakedek.myitemlibrary.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.FileConfiguration;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.handlers.AddItemAllHandlers;
import org.cakedek.myitemlibrary.api.handlers.AddItemHandlers;
import org.cakedek.myitemlibrary.api.handlers.AddItemOnlineHandlers;
import org.cakedek.myitemlibrary.api.handlers.GetShowAllItemsHandlers;
import org.cakedek.myitemlibrary.api.handlers.ItemOperationsHandlers;
import org.cakedek.myitemlibrary.api.handlers.PlayerItemsHandlers;
import org.cakedek.myitemlibrary.util.RateLimiter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class Api {
    /** Keys shorter than this are rejected and a new one is generated. */
    private static final int MIN_API_KEY_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MyItemLibrary plugin;
    private final Gson gson;
    private final RateLimiter rateLimiter;
    private final boolean dosProtectionEnabled;
    private final int maxRequestSizeBytes;

    private HttpServer server;
    private ExecutorService executor;
    private volatile byte[] apiKey = new byte[0];

    public Api(MyItemLibrary plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
        this.dosProtectionEnabled = plugin.isDosProtectionEnabled();
        this.maxRequestSizeBytes = plugin.getMaxRequestSizeBytes();
        this.rateLimiter = new RateLimiter(plugin.getMaxRequestsPerMinute(), plugin.getRequestTimeWindowMs());
    }

    public static String generateApiKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public synchronized void startServer() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("c-api.c-api-enable", false)) {
            return;
        }
        if (server != null) {
            stopServer();
        }

        String host = config.getString("c-api.c-api-host", "127.0.0.1");
        int port = config.getInt("c-api.c-api-port", 1558);

        // An empty key used to let any request with an empty X-API-Key header through.
        String key = config.getString("c-api.c-api-key", "");
        if (key == null || key.trim().length() < MIN_API_KEY_LENGTH) {
            key = generateApiKey();
            config.set("c-api.c-api-key", key);
            plugin.saveConfig();
            plugin.getLogger().warning("c-api.c-api-key was empty or too short (min " + MIN_API_KEY_LENGTH
                    + " characters). A new random key was generated and saved in config.yml.");
        }
        this.apiKey = key.trim().getBytes(StandardCharsets.UTF_8);

        if ("0.0.0.0".equals(host) || "::".equals(host)) {
            plugin.getLogger().warning("The API listens on all network interfaces (" + host
                    + "). Make sure port " + port + " is protected by a firewall, or set c-api.c-api-host to 127.0.0.1.");
        }

        try {
            HttpServer newServer = HttpServer.create(new InetSocketAddress(host, port), 0);
            server = newServer;

            createProtectedContext("/items/", new PlayerItemsHandlers(plugin, this, gson).new PlayerItemsHandler(), false);
            createProtectedContext("/add-item", new AddItemHandlers(plugin, this).new AddItemHandler(), true);
            createProtectedContext("/add-item-all", new AddItemAllHandlers(plugin, this).new AddItemAllHandler(), true);
            createProtectedContext("/add-item-online", new AddItemOnlineHandlers(plugin, this).new AddItemOnlineHandler(), true);
            createProtectedContext("/items", new GetShowAllItemsHandlers(plugin, this, gson).new GetShowAllItemsHandler(), true);
            createProtectedContext("/item/", new ItemOperationsHandlers(plugin, this, gson).new ItemOperationsHandler(), false);

            AtomicInteger threadId = new AtomicInteger();
            executor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "MyItemLibrary-API-" + threadId.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
            newServer.setExecutor(executor);
            newServer.start();
            plugin.getLogger().info("API server started on " + host + ":" + port);
        } catch (IOException | IllegalArgumentException e) {
            server = null;
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
    }

    private void createProtectedContext(String path, HttpHandler handler, boolean exactPath) {
        server.createContext(path, exchange -> {
            try {
                if (exactPath && !path.equals(exchange.getRequestURI().getPath())) {
                    sendResponse(exchange, 404, "Not Found");
                    return;
                }

                if (dosProtectionEnabled) {
                    String remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
                    if (!rateLimiter.allowRequest(remoteAddress)) {
                        sendResponse(exchange, 429, "Too Many Requests");
                        return;
                    }
                }

                if (!isAuthorized(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                // Early reject based on the header; the body read itself is limited too (see readBody),
                // so chunked requests without Content-Length cannot bypass the limit.
                String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
                if (contentLength != null) {
                    try {
                        if (Long.parseLong(contentLength.trim()) > maxRequestSizeBytes) {
                            sendResponse(exchange, 413, "Request Entity Too Large");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendResponse(exchange, 400, "Bad Request");
                        return;
                    }
                }

                handler.handle(exchange);
            } catch (RequestTooLargeException e) {
                safeSend(exchange, 413, "Request Entity Too Large");
            } catch (BadRequestException e) {
                safeSend(exchange, 400, "Bad Request: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error handling API request for path " + path, e);
                safeSend(exchange, 500, "Internal Server Error");
            } finally {
                exchange.close();
            }
        });
    }

    public synchronized void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
            plugin.getLogger().info("API server stopped");
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** Constant-time API key comparison. */
    public boolean isAuthorized(HttpExchange exchange) {
        byte[] expected = apiKey;
        String provided = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (expected.length == 0 || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided.trim().getBytes(StandardCharsets.UTF_8));
    }

    /** Reads and parses a JSON object body, enforcing the configured size limit. */
    public JsonObject parseRequestBody(InputStream requestBody) throws IOException {
        byte[] body = readBody(requestBody);
        try {
            JsonElement element = gson.fromJson(new String(body, StandardCharsets.UTF_8), JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                throw new BadRequestException("Body must be a JSON object");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new BadRequestException("Invalid JSON");
        }
    }

    private byte[] readBody(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxRequestSizeBytes) {
                throw new RequestTooLargeException();
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** Required string field; throws BadRequestException (HTTP 400) if missing or not a string. */
    public static String requireString(JsonObject json, String field) {
        JsonElement e = json.get(field);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new BadRequestException("Field '" + field + "' is required and must be a string");
        }
        return e.getAsString();
    }

    /** Required integer field; throws BadRequestException (HTTP 400) if missing or not a number. */
    public static int requireInt(JsonObject json, String field) {
        JsonElement e = json.get(field);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new BadRequestException("Field '" + field + "' is required and must be a number");
        }
        try {
            return e.getAsInt();
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Field '" + field + "' must be an integer");
        }
    }

    public void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        String trimmed = response.trim();
        boolean json = trimmed.startsWith("{") || trimmed.startsWith("[");
        sendResponse(exchange, statusCode, response, json ? "application/json" : "text/plain");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void safeSend(HttpExchange exchange, int status, String message) {
        try {
            sendResponse(exchange, status, message);
        } catch (IOException ignored) {
            // Response already started or client disconnected.
        }
    }

    /** Thrown by handlers for invalid client input; becomes HTTP 400 with the message. */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    private static class RequestTooLargeException extends RuntimeException {
        RequestTooLargeException() {
            super("Request body too large");
        }
    }
}
