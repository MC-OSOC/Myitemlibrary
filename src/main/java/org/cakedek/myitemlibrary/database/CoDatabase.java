package org.cakedek.myitemlibrary.database;

import org.cakedek.myitemlibrary.MyItemLibrary;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Database access. The connection is shared by the main thread and the API threads,
 * so every method that touches it is synchronized.
 */
public class CoDatabase {
    private static final int MAX_SEARCH_LENGTH = 64;

    private final MyItemLibrary plugin;
    private final String databaseMode;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String databaseName;
    private final boolean useSsl;
    private Connection connection;

    public CoDatabase(MyItemLibrary plugin) {
        this.plugin = plugin;
        this.databaseMode = plugin.getConfig().getString("c-database-mode", "MySQL");
        this.host = plugin.getConfig().getString("c-database.host", "localhost");
        this.port = plugin.getConfig().getInt("c-database.port", 3306);
        this.username = plugin.getConfig().getString("c-database.username", "");
        this.password = plugin.getConfig().getString("c-database.password", "");
        this.databaseName = plugin.getConfig().getString("c-database.database", "myitemlibrary");
        this.useSsl = plugin.getConfig().getBoolean("c-database.use-ssl", false);
    }

    private boolean isMySql() {
        return "MySQL".equalsIgnoreCase(databaseMode);
    }

    public synchronized boolean connect() {
        try {
            if (isMySql()) {
                String url = "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                        + "?useUnicode=true&characterEncoding=utf8"
                        + "&useSSL=" + useSsl
                        + "&allowLoadLocalInfile=false"
                        + "&autoReconnect=true";
                connection = DriverManager.getConnection(url, username, password);
            } else if ("Local".equalsIgnoreCase(databaseMode)) {
                String url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "database.db").getAbsolutePath();
                connection = DriverManager.getConnection(url);
            } else {
                plugin.getLogger().severe("Invalid database mode specified in config: " + databaseMode);
                return false;
            }
            return true;
        } catch (SQLException e) {
            // Do not print the URL/credentials, only the reason.
            plugin.getLogger().severe("Could not connect to the database: " + e.getMessage());
            return false;
        }
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error while closing the database connection", e);
            }
            connection = null;
        }
    }

    /** Reconnects if the connection was dropped (e.g. MySQL wait_timeout). */
    private Connection conn() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            close();
            if (!connect()) {
                throw new SQLException("Database is not connected");
            }
        }
        return connection;
    }

    public synchronized boolean createTableIfNotExists() {
        String createTableSQL;
        if (isMySql()) {
            createTableSQL = "CREATE TABLE IF NOT EXISTS co_list_item ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "item_name VARCHAR(255), "
                    + "item_display VARCHAR(255), "
                    + "description TEXT, "
                    + "player VARCHAR(255), "
                    + "enable BOOLEAN, "
                    + "command TEXT, "
                    + "used INT"
                    + ")";
        } else {
            createTableSQL = "CREATE TABLE IF NOT EXISTS co_list_item ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "item_name TEXT, "
                    + "item_display TEXT, "
                    + "description TEXT, "
                    + "player TEXT, "
                    + "enable INTEGER, "
                    + "command TEXT, "
                    + "used INTEGER"
                    + ")";
        }
        try (Statement stmt = conn().createStatement()) {
            stmt.execute(createTableSQL);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create table co_list_item", e);
            return false;
        }
    }

    public synchronized boolean isConnectionValid() {
        try {
            return connection != null && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////

    public synchronized void addItem(String itemName, String itemDisplay, String description, String player, boolean enable, String command, int used) throws SQLException {
        String insertSQL = "INSERT INTO co_list_item (item_name, item_display, description, player, enable, command, used) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn().prepareStatement(insertSQL)) {
            pstmt.setString(1, itemName);
            pstmt.setString(2, itemDisplay);
            pstmt.setString(3, description);
            pstmt.setString(4, player);
            pstmt.setBoolean(5, enable);
            pstmt.setString(6, command);
            pstmt.setInt(7, used);
            pstmt.executeUpdate();
        }
    }

    public synchronized List<ItemData> getAllItems() throws SQLException {
        try (Statement stmt = conn().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM co_list_item")) {
            return readItems(rs);
        }
    }

    public synchronized List<ItemData> getItemsByPlayer(String playerName) throws SQLException {
        try (PreparedStatement pstmt = conn().prepareStatement("SELECT * FROM co_list_item WHERE player = ?")) {
            pstmt.setString(1, playerName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return readItems(rs);
            }
        }
    }

    public synchronized ItemData getItem(int id) throws SQLException {
        try (PreparedStatement pstmt = conn().prepareStatement("SELECT * FROM co_list_item WHERE id = ?")) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? createItemDataFromResultSet(rs) : null;
            }
        }
    }

    public synchronized boolean deleteItem(int id) throws SQLException {
        try (PreparedStatement pstmt = conn().prepareStatement("DELETE FROM co_list_item WHERE id = ?")) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    public synchronized List<String> getAllPlayersEverJoined() throws SQLException {
        List<String> players = new ArrayList<>();
        try (Statement stmt = conn().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DISTINCT player FROM co_list_item")) {
            while (rs.next()) {
                players.add(rs.getString("player"));
            }
        }
        return players;
    }

    /** Enabled (unclaimed) items for a player, optionally filtered by a search term. */
    public synchronized List<ItemData> getEnabledItems(String player, String searchTerm) throws SQLException {
        String term = searchTerm == null ? "" : searchTerm;
        if (term.length() > MAX_SEARCH_LENGTH) {
            term = term.substring(0, MAX_SEARCH_LENGTH);
        }
        // '!' is used as escape character because it works the same in MySQL and SQLite.
        String like = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String querySQL = "SELECT * FROM co_list_item WHERE player = ? AND enable = ? "
                + "AND (item_name LIKE ? ESCAPE '!' OR description LIKE ? ESCAPE '!')";
        try (PreparedStatement pstmt = conn().prepareStatement(querySQL)) {
            pstmt.setString(1, player);
            pstmt.setBoolean(2, true);
            pstmt.setString(3, like);
            pstmt.setString(4, like);
            try (ResultSet rs = pstmt.executeQuery()) {
                return readItems(rs);
            }
        }
    }

    /**
     * Atomically claims an item: it must belong to the player and still be enabled.
     * Returns the item's command, or null if it was already claimed / not owned by the player.
     * This prevents claiming the same reward twice (double click, two open GUIs, etc).
     */
    public synchronized String claimItem(int id, String player) throws SQLException {
        Connection c = conn();
        String command;
        try (PreparedStatement select = c.prepareStatement(
                "SELECT command FROM co_list_item WHERE id = ? AND player = ? AND enable = ?")) {
            select.setInt(1, id);
            select.setString(2, player);
            select.setBoolean(3, true);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                command = rs.getString("command");
            }
        }
        try (PreparedStatement update = c.prepareStatement(
                "UPDATE co_list_item SET enable = ?, used = used - 1 WHERE id = ? AND player = ? AND enable = ?")) {
            update.setBoolean(1, false);
            update.setInt(2, id);
            update.setString(3, player);
            update.setBoolean(4, true);
            return update.executeUpdate() == 1 ? command : null;
        }
    }

    public synchronized void updateItemEnabled(int id, boolean enabled) throws SQLException {
        try (PreparedStatement pstmt = conn().prepareStatement("UPDATE co_list_item SET enable = ? WHERE id = ?")) {
            pstmt.setBoolean(1, enabled);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        }
    }

    private List<ItemData> readItems(ResultSet rs) throws SQLException {
        List<ItemData> items = new ArrayList<>();
        while (rs.next()) {
            items.add(createItemDataFromResultSet(rs));
        }
        return items;
    }

    private ItemData createItemDataFromResultSet(ResultSet rs) throws SQLException {
        return new ItemData(
                rs.getInt("id"),
                rs.getString("item_name"),
                rs.getString("item_display"),
                rs.getString("description"),
                rs.getString("player"),
                rs.getBoolean("enable"),
                rs.getString("command"),
                rs.getInt("used")
        );
    }
}
