package org.cakedek.myitemlibrary;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("CallToPrintStackTrace")
public class CoDatabase {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String databaseName;
    private Connection connection;

    public CoDatabase(String host, int port, String username, String password, String databaseName) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.databaseName = databaseName;
    }

    public boolean connect() {
        try {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false&autoReconnect=true";
            connection = DriverManager.getConnection(url, username, password);
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
        String createTableSQL = "CREATE TABLE IF NOT EXISTS MyItemLibrary ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "item_name VARCHAR(255), "
                + "item_display VARCHAR(255), "
                + "description TEXT, "
                + "player VARCHAR(255), "
                + "enable BOOLEAN, "
                + "command TEXT, "
                + "used INT"
                + ")";
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
