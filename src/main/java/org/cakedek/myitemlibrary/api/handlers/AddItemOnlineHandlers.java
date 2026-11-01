package org.cakedek.myitemlibrary.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.CoDatabase;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

            ItemRequest item = ItemRequest.parse(api.parseRequestBody(exchange.getRequestBody()));

            List<String> onlinePlayers;
            try {
                // The online player list must be read on the main server thread.
                onlinePlayers = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        names.add(p.getName());
                    }
                    return names;
                }).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                api.sendResponse(exchange, 503, "Service Unavailable");
                return;
            } catch (ExecutionException | TimeoutException e) {
                plugin.getLogger().warning("Could not read online players for API request: " + e);
                api.sendResponse(exchange, 503, "Service Unavailable");
                return;
            }

            try {
                CoDatabase database = plugin.getDatabase();
                int addedCount = 0;
                for (String playerName : onlinePlayers) {
                    database.addItem(item.itemName(), item.itemDisplay(), item.description(), playerName, true, item.command(), item.used());
                    addedCount++;
                }
                api.sendResponse(exchange, 200, "Item added for " + addedCount + " online players");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding item for online players via API: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
}
