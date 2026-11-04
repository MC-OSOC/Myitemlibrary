package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.database.ItemOwner;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

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
            String uuid = ItemRequest.parsePlayerUuid(json);
            String orderId = ItemRequest.parseOrderId(json);

            ItemOwner owner;
            if (uuid != null) {
                owner = new ItemOwner(player, uuid);
            } else {
                // No Mojang lookup: if the player is online use their UUID, otherwise the item stays
                // unbound and is bound by name the next time the player joins.
                ItemOwner online = plugin.getOnlinePlayers().find(player);
                owner = online != null ? online : new ItemOwner(player, null);
            }

            try {
                CoDatabase.AddResult result = api.database().addItems(item.itemName(), item.itemDisplay(),
                        item.description(), item.command(), item.used(), List.of(owner), orderId, "add-item");
                api.sendAddResult(exchange, result, orderId, "Item added successfully");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding item via API: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
}
