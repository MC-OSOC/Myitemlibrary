package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.ClaimRecord;
import org.cakedek.myitemlibrary.util.Input;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GET /claims: the claim log, newest first, with optional filters. */
public class ClaimsHandlers {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;
    private static final Set<String> PARAMS = Set.of("player", "uuid", "order_id", "since", "limit");

    private final MyItemLibrary plugin;
    private final Api api;
    private final Gson gson;

    public ClaimsHandlers(MyItemLibrary plugin, Api api, Gson gson) {
        this.plugin = plugin;
        this.api = api;
        this.gson = gson;
    }

    public class ClaimsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                api.sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());

            String player = query.get("player");
            if (player != null && !Input.isValidPlayerName(player)) {
                throw new Api.BadRequestException("Invalid player name");
            }
            String uuid = query.get("uuid");
            if (uuid != null) {
                if (!Input.isValidUuid(uuid)) {
                    throw new Api.BadRequestException("Invalid uuid");
                }
                uuid = Input.normalizeUuid(uuid);
            }
            String orderId = query.get("order_id");
            if (orderId != null && !Input.isValidOrderId(orderId)) {
                throw new Api.BadRequestException("Invalid order_id");
            }
            Long since = query.containsKey("since") ? parseLong(query.get("since"), "since", 0, Long.MAX_VALUE) : null;
            int limit = query.containsKey("limit")
                    ? (int) parseLong(query.get("limit"), "limit", 1, MAX_LIMIT)
                    : DEFAULT_LIMIT;

            try {
                List<ClaimRecord> claims = api.database().getClaims(player, uuid, orderId, since, limit);
                JsonArray out = new JsonArray();
                for (ClaimRecord c : claims) {
                    JsonObject o = new JsonObject();
                    o.addProperty("id", c.id());
                    o.addProperty("item_id", c.itemId());
                    o.addProperty("player_uuid", c.playerUuid());
                    o.addProperty("player_name", c.playerName());
                    o.addProperty("item_name", c.itemName());
                    o.addProperty("command", c.command());
                    o.addProperty("order_id", c.orderId());
                    o.addProperty("claimed_at", c.claimedAt());
                    out.add(o);
                }
                api.sendResponse(exchange, 200, gson.toJson(out));
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching claim log: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    /** Parses the query string; unknown, repeated or malformed parameters are rejected with 400. */
    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&", -1)) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new Api.BadRequestException("Malformed query parameter");
            }
            String key;
            String value;
            try {
                key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new Api.BadRequestException("Malformed query parameter");
            }
            if (!PARAMS.contains(key)) {
                throw new Api.BadRequestException("Unknown query parameter (allowed: player, uuid, order_id, since, limit)");
            }
            if (params.put(key, value) != null) {
                throw new Api.BadRequestException("Query parameter '" + key + "' given more than once");
            }
        }
        return params;
    }

    private static long parseLong(String value, String name, long min, long max) {
        long v;
        try {
            v = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new Api.BadRequestException("'" + name + "' must be an integer");
        }
        if (v < min || v > max) {
            throw new Api.BadRequestException("'" + name + "' must be between " + min + " and " + max);
        }
        return v;
    }
}
