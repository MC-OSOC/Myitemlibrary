package org.cakedek.myitemlibrary.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Database access through a HikariCP pool.
 * <p>
 * MySQL uses a small pool. SQLite uses a pool of exactly one connection, so all SQLite access is
 * serialized. Every method may block on I/O: call them from async tasks or API threads, never from
 * the server main thread (except {@link #open()} / {@link #close()} at startup, reload and shutdown).
 * <p>
 * Methods that change several rows run in one transaction.
 */
public class CoDatabase implements AutoCloseable {
    private static final int MAX_SEARCH_LENGTH = 64;
    private static final int MYSQL_POOL_SIZE = 6;
    private static final String PUBLIC_KEY_ERROR = "Public Key Retrieval is not allowed";

    /** Owner check used by listing and claiming: bound to this UUID, or not bound yet and the name matches. */
    private static final String OWNER_CONDITION =
            "(player_uuid = ? OR (player_uuid IS NULL AND LOWER(player) = LOWER(?)))";

    private final DatabaseSettings settings;
    private final Logger logger;
    private HikariDataSource dataSource;

    public CoDatabase(DatabaseSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Opens the pool and creates/migrates the tables. Returns false (and logs why) on failure. */
    public boolean open() {
        HikariConfig config = new HikariConfig();
        if (settings.mysql()) {
            loadDriver("com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver");
            config.setPoolName("MyItemLibrary-MySQL");
            config.setJdbcUrl(mysqlJdbcUrl(settings));
            if (!settings.useSsl() && !isLoopbackHost(settings.host())) {
                logger.warning("c-database.use-ssl is false but the MySQL server (" + settings.host() + ") is not on this "
                        + "machine: the connection is not encrypted. Set c-database.use-ssl: true if the network is not trusted.");
            }
            config.setUsername(settings.username());
            config.setPassword(settings.password());
            config.setMaximumPoolSize(MYSQL_POOL_SIZE);
            config.setMinimumIdle(1);
        } else {
            loadDriver("org.sqlite.JDBC");
            File parent = settings.sqliteFile().getAbsoluteFile().getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            config.setPoolName("MyItemLibrary-SQLite");
            config.setJdbcUrl("jdbc:sqlite:" + settings.sqliteFile().getAbsolutePath());
            // SQLite allows only one writer; one shared connection avoids SQLITE_BUSY inside this process.
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
        }
        config.setConnectionTimeout(10_000);

        try {
            dataSource = new HikariDataSource(config);
        } catch (RuntimeException e) {
            // Do not print the URL/credentials, only the reason.
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.severe("Could not connect to the database: " + cause.getMessage());
            if (causeMentions(e, PUBLIC_KEY_ERROR)) {
                logger.severe("Hint: MySQL 8 needs SSL or public key retrieval for this login. Set c-database.use-ssl: true "
                        + "(recommended) or c-database.allow-public-key-retrieval: true in config.yml, then /my-library-reload.");
            }
            return false;
        }

        try (Connection c = dataSource.getConnection()) {
            createTables(c);
            migrate(c);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create or migrate the database tables", e);
            close();
            return false;
        }
    }

    /** The JDBC URL the plugin uses for MySQL/MariaDB (also used by the tests). */
    public static String mysqlJdbcUrl(DatabaseSettings s) {
        StringBuilder url = new StringBuilder("jdbc:mysql://")
                .append(s.host()).append(':').append(s.port()).append('/').append(s.databaseName())
                .append("?useUnicode=true&characterEncoding=utf8")
                .append("&useSSL=").append(s.useSsl())
                .append("&allowLoadLocalInfile=false");
        if (!s.useSsl() && s.allowPublicKeyRetrieval()) {
            // MySQL 8 caching_sha2_password: without SSL the driver must fetch the server's RSA key to send the
            // password on the first login after a server start. Without this the login fails with
            // "Public Key Retrieval is not allowed". Not needed (and not sent) when SSL is on.
            url.append("&allowPublicKeyRetrieval=true");
        }
        return url.toString();
    }

    /** True for localhost and loopback IP literals (127.0.0.0/8, ::1). No DNS lookup. */
    static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.trim().toLowerCase(java.util.Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        return h.equals("localhost") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1")
                || h.matches("127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    }

    private static boolean causeMentions(Throwable t, String text) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static void loadDriver(String... classNames) {
        for (String name : classNames) {
            try {
                Class.forName(name);
                return;
            } catch (ClassNotFoundException ignored) {
                // try the next one
            }
        }
    }

    @Override
    public void close() {
        HikariDataSource ds = dataSource;
        dataSource = null;
        if (ds != null) {
            ds.close();
        }
    }

    public boolean isOpen() {
        HikariDataSource ds = dataSource;
        return ds != null && !ds.isClosed();
    }

    private Connection conn() throws SQLException {
        HikariDataSource ds = dataSource;
        if (ds == null || ds.isClosed()) {
            throw new SQLException("Database is not connected");
        }
        return ds.getConnection();
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ------------------------------------------------------------------ schema

    private void createTables(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            if (settings.mysql()) {
                // co_list_item keeps its 1.3 shape here; new columns are added by migrate().
                st.execute("CREATE TABLE IF NOT EXISTS co_list_item ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "item_name VARCHAR(255), "
                        + "item_display VARCHAR(255), "
                        + "description TEXT, "
                        + "player VARCHAR(255), "
                        + "enable BOOLEAN, "
                        + "command TEXT, "
                        + "used INT)");
                st.execute("CREATE TABLE IF NOT EXISTS co_orders ("
                        + "order_id VARCHAR(64) NOT NULL PRIMARY KEY, "
                        + "endpoint VARCHAR(32) NOT NULL, "
                        + "item_count INT NOT NULL, "
                        + "created_at BIGINT NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS co_claim_log ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "item_id INT NOT NULL, "
                        + "player_uuid VARCHAR(36), "
                        + "player_name VARCHAR(255), "
                        + "item_name VARCHAR(255), "
                        + "command TEXT, "
                        + "order_id VARCHAR(64), "
                        + "claimed_at BIGINT NOT NULL, "
                        + "INDEX idx_claim_log_claimed_at (claimed_at), "
                        + "INDEX idx_claim_log_player_uuid (player_uuid), "
                        + "INDEX idx_claim_log_order_id (order_id))");
            } else {
                st.execute("CREATE TABLE IF NOT EXISTS co_list_item ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "item_name TEXT, "
                        + "item_display TEXT, "
                        + "description TEXT, "
                        + "player TEXT, "
                        + "enable INTEGER, "
                        + "command TEXT, "
                        + "used INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS co_orders ("
                        + "order_id TEXT NOT NULL PRIMARY KEY, "
                        + "endpoint TEXT NOT NULL, "
                        + "item_count INTEGER NOT NULL, "
                        + "created_at INTEGER NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS co_claim_log ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "item_id INTEGER NOT NULL, "
                        + "player_uuid TEXT, "
                        + "player_name TEXT, "
                        + "item_name TEXT, "
                        + "command TEXT, "
                        + "order_id TEXT, "
                        + "claimed_at INTEGER NOT NULL)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_claim_log_claimed_at ON co_claim_log (claimed_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_claim_log_player_uuid ON co_claim_log (player_uuid)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_claim_log_order_id ON co_claim_log (order_id)");
            }
        }
    }

    /**
     * Idempotent schema migration from 1.3 (and fresh tables): adds the new co_list_item columns and indexes
     * if they are missing. Safe when two servers sharing one MySQL database start at the same time.
     */
    private void migrate(Connection c) throws SQLException {
        addColumnIfMissing(c, "co_list_item", "player_uuid", "VARCHAR(36) NULL");
        addColumnIfMissing(c, "co_list_item", "order_id", "VARCHAR(64) NULL");
        addIndexIfMissing(c, "co_list_item", "idx_co_list_item_player_uuid", "player_uuid");
        addIndexIfMissing(c, "co_list_item", "idx_co_list_item_order_id", "order_id");
    }

    private void addColumnIfMissing(Connection c, String table, String column, String definition) throws SQLException {
        if (columnExists(c, table, column)) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            logger.info("Database migration: added column " + table + "." + column);
        } catch (SQLException e) {
            // Another server sharing the database may have added it at the same moment.
            if (!columnExists(c, table, column)) {
                throw e;
            }
        }
    }

    private void addIndexIfMissing(Connection c, String table, String index, String column) throws SQLException {
        if (indexExists(c, table, index)) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.execute("CREATE INDEX " + index + " ON " + table + " (" + column + ")");
            logger.info("Database migration: added index " + index);
        } catch (SQLException e) {
            if (!indexExists(c, table, index)) {
                throw e;
            }
        }
    }

    private static boolean columnExists(Connection c, String table, String column) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getColumns(c.getCatalog(), null, table, null)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean indexExists(Connection c, String table, String index) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getIndexInfo(c.getCatalog(), null, table, false, false)) {
            while (rs.next()) {
                if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ items

    /** Result of {@link #addItems}: replay is true if the order id was already used (nothing was added). */
    public record AddResult(boolean replay, int count) {
    }

    /**
     * Adds one item row per owner. With an order id, the order row and the item rows are inserted in one
     * transaction; if the order id already exists nothing is added and {@code replay} is true.
     * The order's primary key decides, so concurrent duplicate requests also add only one set of items.
     */
    public AddResult addItems(String itemName, String itemDisplay, String description, String command, int used,
                              List<ItemOwner> owners, String orderId, String endpoint) throws SQLException {
        return inTransaction(c -> {
            if (orderId != null) {
                String insertOrder = (settings.mysql() ? "INSERT IGNORE" : "INSERT OR IGNORE")
                        + " INTO co_orders (order_id, endpoint, item_count, created_at) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(insertOrder)) {
                    ps.setString(1, orderId);
                    ps.setString(2, endpoint);
                    ps.setInt(3, owners.size());
                    ps.setLong(4, System.currentTimeMillis());
                    if (ps.executeUpdate() == 0) {
                        return new AddResult(true, orderItemCount(c, orderId));
                    }
                }
            }
            String insertItem = "INSERT INTO co_list_item (item_name, item_display, description, player, player_uuid, "
                    + "enable, command, used, order_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(insertItem)) {
                for (ItemOwner owner : owners) {
                    ps.setString(1, itemName);
                    ps.setString(2, itemDisplay);
                    ps.setString(3, description);
                    ps.setString(4, owner.name());
                    ps.setString(5, owner.uuid());
                    ps.setBoolean(6, true);
                    ps.setString(7, command);
                    ps.setInt(8, used);
                    ps.setString(9, orderId);
                    ps.addBatch();
                }
                if (!owners.isEmpty()) {
                    ps.executeBatch();
                }
            }
            return new AddResult(false, owners.size());
        });
    }

    private static int orderItemCount(Connection c, String orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT item_count FROM co_orders WHERE order_id = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<ItemData> getAllItems() throws SQLException {
        try (Connection c = conn();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM co_list_item")) {
            return readItems(rs);
        }
    }

    /** Items whose stored player name matches (case-insensitive). */
    public List<ItemData> getItemsByPlayer(String playerName) throws SQLException {
        return queryItems("SELECT * FROM co_list_item WHERE LOWER(player) = LOWER(?)", playerName);
    }

    public List<ItemData> getItemsByUuid(String uuid) throws SQLException {
        return queryItems("SELECT * FROM co_list_item WHERE player_uuid = ?", uuid);
    }

    private List<ItemData> queryItems(String sql, String param) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return readItems(rs);
            }
        }
    }

    public ItemData getItem(int id) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM co_list_item WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readItem(rs) : null;
            }
        }
    }

    public boolean deleteItem(int id) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("DELETE FROM co_list_item WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Everyone who ever had an item, one entry per person: bound owners by UUID (with their newest name),
     * unbound names only if no bound owner has that name.
     */
    public List<ItemOwner> getAllOwners() throws SQLException {
        Map<String, ItemOwner> byUuid = new LinkedHashMap<>();
        Map<String, ItemOwner> byName = new LinkedHashMap<>();
        try (Connection c = conn();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT player, player_uuid FROM co_list_item ORDER BY id DESC")) {
            while (rs.next()) {
                String name = rs.getString("player");
                String uuid = rs.getString("player_uuid");
                if (name == null) {
                    continue;
                }
                if (uuid != null) {
                    byUuid.putIfAbsent(uuid, new ItemOwner(name, uuid));
                } else {
                    byName.putIfAbsent(name.toLowerCase(Locale.ROOT), new ItemOwner(name, null));
                }
            }
        }
        Set<String> boundNames = new HashSet<>();
        for (ItemOwner owner : byUuid.values()) {
            boundNames.add(owner.name().toLowerCase(Locale.ROOT));
        }
        List<ItemOwner> owners = new ArrayList<>(byUuid.values());
        for (Map.Entry<String, ItemOwner> e : byName.entrySet()) {
            if (!boundNames.contains(e.getKey())) {
                owners.add(e.getValue());
            }
        }
        return owners;
    }

    /**
     * Called when a player joins: binds their not-yet-bound items (matched by name, case-insensitive)
     * to the UUID, and renames rows already bound to the UUID to the current name.
     * Returns the number of rows bound to the UUID by name.
     */
    public int bindPlayer(String uuid, String name) throws SQLException {
        return inTransaction(c -> {
            int bound;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE co_list_item SET player_uuid = ?, player = ? WHERE player_uuid IS NULL AND LOWER(player) = LOWER(?)")) {
                ps.setString(1, uuid);
                ps.setString(2, name);
                ps.setString(3, name);
                bound = ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE co_list_item SET player = ? WHERE player_uuid = ?")) {
                // No "player <> ?" filter: MySQL compares case-insensitively and would skip case-only renames.
                ps.setString(1, name);
                ps.setString(2, uuid);
                ps.executeUpdate();
            }
            return bound;
        });
    }

    /** Enabled (unclaimed) items of a player, optionally filtered by a search term. */
    public List<ItemData> getEnabledItems(String uuid, String name, String searchTerm) throws SQLException {
        String term = searchTerm == null ? "" : searchTerm;
        if (term.length() > MAX_SEARCH_LENGTH) {
            term = term.substring(0, MAX_SEARCH_LENGTH);
        }
        // '!' is used as escape character because it works the same in MySQL and SQLite.
        String like = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String sql = "SELECT * FROM co_list_item WHERE " + OWNER_CONDITION + " AND enable = ? "
                + "AND (item_name LIKE ? ESCAPE '!' OR description LIKE ? ESCAPE '!') ORDER BY id";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setBoolean(3, true);
            ps.setString(4, like);
            ps.setString(5, like);
            try (ResultSet rs = ps.executeQuery()) {
                return readItems(rs);
            }
        }
    }

    // ------------------------------------------------------------------ claims

    /**
     * A successful claim: the command to run (with {@code <player>} replaced and no leading slash)
     * and the claim log id (needed to undo the claim if the command cannot be run).
     */
    public record Claim(long logId, String itemName, String command) {
    }

    /**
     * Atomically claims an item for a player and writes the claim log row in the same transaction.
     * The item must belong to the player (by UUID, or by name if still unbound) and still be enabled.
     * Returns null if it was already claimed or is not the player's item, so a reward is never given twice.
     */
    public Claim claimItem(int id, String uuid, String name) throws SQLException {
        return inTransaction(c -> {
            // The conditional UPDATE runs first so it takes the row lock: a second concurrent claim waits and then
            // updates 0 rows.
            try (PreparedStatement ps = c.prepareStatement("UPDATE co_list_item SET enable = ?, used = used - 1 "
                    + "WHERE id = ? AND enable = ? AND " + OWNER_CONDITION)) {
                ps.setBoolean(1, false);
                ps.setInt(2, id);
                ps.setBoolean(3, true);
                ps.setString(4, uuid);
                ps.setString(5, name);
                if (ps.executeUpdate() != 1) {
                    return null;
                }
            }
            String itemName;
            String rawCommand;
            String orderId;
            try (PreparedStatement ps = c.prepareStatement("SELECT item_name, command, order_id FROM co_list_item WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Claimed item " + id + " disappeared");
                    }
                    itemName = rs.getString("item_name");
                    rawCommand = rs.getString("command");
                    orderId = rs.getString("order_id");
                }
            }
            String command = rawCommand == null ? "" : rawCommand.replace("<player>", name);
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            long logId;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO co_claim_log (item_id, player_uuid, player_name, "
                    + "item_name, command, order_id, claimed_at) VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, id);
                ps.setString(2, uuid);
                ps.setString(3, name);
                ps.setString(4, itemName);
                ps.setString(5, command);
                ps.setString(6, orderId);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("No id returned for claim log row");
                    }
                    logId = keys.getLong(1);
                }
            }
            return new Claim(logId, itemName, command);
        });
    }

    /** Reverts a claim whose command could not be run (e.g. the player left before it ran). */
    public void undoClaim(int itemId, long logId) throws SQLException {
        inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE co_list_item SET enable = ?, used = used + 1 WHERE id = ? AND enable = ?")) {
                ps.setBoolean(1, true);
                ps.setInt(2, itemId);
                ps.setBoolean(3, false);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM co_claim_log WHERE id = ?")) {
                ps.setLong(1, logId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Claim log rows, newest first. Every filter is optional (null). */
    public List<ClaimRecord> getClaims(String playerName, String uuid, String orderId, Long since, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM co_claim_log WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (playerName != null) {
            sql.append(" AND LOWER(player_name) = LOWER(?)");
            params.add(playerName);
        }
        if (uuid != null) {
            sql.append(" AND player_uuid = ?");
            params.add(uuid);
        }
        if (orderId != null) {
            sql.append(" AND order_id = ?");
            params.add(orderId);
        }
        if (since != null) {
            sql.append(" AND claimed_at >= ?");
            params.add(since);
        }
        sql.append(" ORDER BY claimed_at DESC, id DESC LIMIT ?");
        params.add(limit);

        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String s) {
                    ps.setString(i + 1, s);
                } else if (p instanceof Long l) {
                    ps.setLong(i + 1, l);
                } else {
                    ps.setInt(i + 1, (Integer) p);
                }
            }
            List<ClaimRecord> claims = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    claims.add(new ClaimRecord(rs.getLong("id"), rs.getInt("item_id"), rs.getString("player_uuid"),
                            rs.getString("player_name"), rs.getString("item_name"), rs.getString("command"),
                            rs.getString("order_id"), rs.getLong("claimed_at")));
                }
            }
            return claims;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static List<ItemData> readItems(ResultSet rs) throws SQLException {
        List<ItemData> items = new ArrayList<>();
        while (rs.next()) {
            items.add(readItem(rs));
        }
        return items;
    }

    private static ItemData readItem(ResultSet rs) throws SQLException {
        return new ItemData(
                rs.getInt("id"),
                rs.getString("item_name"),
                rs.getString("item_display"),
                rs.getString("description"),
                rs.getString("player"),
                rs.getString("player_uuid"),
                rs.getBoolean("enable"),
                rs.getString("command"),
                rs.getInt("used"),
                rs.getString("order_id")
        );
    }
}
