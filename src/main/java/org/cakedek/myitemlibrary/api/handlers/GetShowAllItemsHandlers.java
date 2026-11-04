package org.cakedek.myitemlibrary.api.handlers;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.database.ItemData;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class GetShowAllItemsHandlers {
    private final MyItemLibrary plugin;
    private final Api api;
    private final Gson gson;

    public GetShowAllItemsHandlers(MyItemLibrary plugin, Api api, Gson gson) {
        this.plugin = plugin;
        this.api = api;
        this.gson = gson;
    }

    public class GetShowAllItemsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                api.sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                List<ItemData> items = api.database().getAllItems();
                api.sendResponse(exchange, 200, gson.toJson(items));
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching items: " + e.getMessage());
                api.sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
}
