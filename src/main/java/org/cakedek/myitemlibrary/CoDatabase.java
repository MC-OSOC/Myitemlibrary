package org.cakedek.myitemlibrary;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

@SuppressWarnings("CallToPrintStackTrace")
public class CoDatabase {
    private final MyItemLibrary plugin;
    private final String databaseMode;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String databaseName;
    private Connection connection;


    // Constructor for MySQL mode (existing constructor)
    public CoDatabase(String host, int port, String username, String password, String databaseName) {
        this.plugin = null;
        this.databaseMode = "MySQL";
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.databaseName = databaseName;
    }

    public CoDatabase(MyItemLibrary plugin) {
        this.plugin = plugin;
        this.databaseMode = plugin.getConfig().getString("c-database-mode", "MySQL");
        this.host = plugin.getConfig().getString("c-database.host", "localhost");
        this.port = plugin.getConfig().getInt("c-database.port", 3306);
        this.username = plugin.getConfig().getString("c-database.username", "");
        this.password = plugin.getConfig().getString("c-database.password", "");
        this.databaseName = plugin.getConfig().getString("c-database.database", "myitemlibrary");
    }

    public boolean connect() {
        try {
            if ("MySQL".equalsIgnoreCase(databaseMode)) {
                String url = "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                        + "?useUnicode=true&characterEncoding=utf8&useSSL=false&autoReconnect=true";
                connection = DriverManager.getConnection(url, username, password);
            } else if ("Local".equalsIgnoreCase(databaseMode)) {
                if (plugin == null) {
                    throw new IllegalStateException("Plugin instance is required for Local database mode");
                }
                String url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "database.db").getAbsolutePath();
                connection = DriverManager.getConnection(url);
            } else {
                if (plugin != null) {
                    plugin.getLogger().severe("Invalid database mode specified in config: " + databaseMode);
                }
                return false;
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void createTableIfNotExists() {
        String createTableSQL;
        if ("MySQL".equalsIgnoreCase(databaseMode)) {
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
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnectionValid() {
        try {
            return connection != null && connection.isValid(2);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////

    public void addItem(String itemName, String itemDisplay, String description, String player, boolean enable, String command, int used) throws SQLException {
        String insertSQL = "INSERT INTO co_list_item (item_name, item_display, description, player, enable, command, used) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
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

    public List<ItemData> getAllItems() throws SQLException {
        List<ItemData> items = new ArrayList<>();
        String query = "SELECT * FROM co_list_item";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                items.add(createItemDataFromResultSet(rs));
            }
        }
        return items;
    }

    public List<ItemData> getItemsByPlayer(String playerName) throws SQLException {
        List<ItemData> items = new ArrayList<>();
        String query = "SELECT * FROM co_list_item WHERE player = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, playerName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    items.add(createItemDataFromResultSet(rs));
                }
            }
        }
        return items;
    }

    public ItemData getItem(int id) throws SQLException {
        String query = "SELECT * FROM co_list_item WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return createItemDataFromResultSet(rs);
                }
            }
        }
        return null;
    }

    public boolean deleteItem(int id) throws SQLException {
        String query = "DELETE FROM co_list_item WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, id);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public List<String> getAllPlayersEverJoined() throws SQLException {
        List<String> players = new ArrayList<>();
        String query = "SELECT DISTINCT player FROM co_list_item";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                players.add(rs.getString("player"));
            }
        }
        return players;
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

    //  สำหรับค้นหาไอเทม
    public ResultSet getListItemsByPlayerAndEnabledAndSearch(String player, String searchTerm) throws SQLException {
        String querySQL = "SELECT * FROM co_list_item WHERE player = ? AND enable = 1 AND (item_name LIKE ? OR description LIKE ?)";
        PreparedStatement pstmt = connection.prepareStatement(querySQL);
        pstmt.setString(1, player);
        pstmt.setString(2, "%" + searchTerm + "%");
        pstmt.setString(3, "%" + searchTerm + "%");
        return pstmt.executeQuery();
    }

    ///////////////////////////////////////////////////////////////////////


    public ResultSet getListItemsByPlayerAndEnabled(String player) throws SQLException {
        String querySQL = "SELECT * FROM co_list_item WHERE player = '" + player + "' AND enable = 1";
        Statement stmt = connection.createStatement();
        return stmt.executeQuery(querySQL);
    }

    public void updateItemEnabled(int id, boolean enabled) throws SQLException {
        String updateSQL = "UPDATE co_list_item SET enable = " + (enabled ? 1 : 0) + " WHERE id = " + id;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(updateSQL);
        }
    }

    public void updateItemused(int id) throws SQLException {
        String updateSQL = "UPDATE co_list_item SET used = used - 1 WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }


}
