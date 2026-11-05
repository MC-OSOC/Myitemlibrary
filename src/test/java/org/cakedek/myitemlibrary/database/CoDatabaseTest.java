package org.cakedek.myitemlibrary.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database layer tests. Always run against SQLite; also against MySQL when MIL_TEST_MYSQL_HOST is set
 * (CI runs them against MySQL and MariaDB). MIL_TEST_MYSQL_PORT/USER/PASSWORD/DATABASE/SSL default to
 * 3306/mil/milpass/mil/true; MariaDB is tested with MIL_TEST_MYSQL_SSL=false.
 */
class CoDatabaseTest {
    private static final Logger LOG = Logger.getLogger("CoDatabaseTest");
    private static final String UUID_A = "11111111-2222-3333-4444-555555555555";
    private static final String UUID_B = "99999999-2222-3333-4444-555555555555";

    @TempDir
    static Path tempDir;

    private final List<CoDatabase> opened = new ArrayList<>();

    static Stream<String> backends() {
        return mysqlConfigured() ? Stream.of("sqlite", "mysql") : Stream.of("sqlite");
    }

    private static boolean mysqlConfigured() {
        String host = System.getenv("MIL_TEST_MYSQL_HOST");
        return host != null && !host.isEmpty();
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isEmpty() ? def : v;
    }

    private static int counter;

    private DatabaseSettings settings(String backend) {
        if ("mysql".equals(backend)) {
            return new DatabaseSettings(true, env("MIL_TEST_MYSQL_HOST", "127.0.0.1"),
                    Integer.parseInt(env("MIL_TEST_MYSQL_PORT", "3306")), env("MIL_TEST_MYSQL_USER", "mil"),
                    env("MIL_TEST_MYSQL_PASSWORD", "milpass"), env("MIL_TEST_MYSQL_DATABASE", "mil"),
                    Boolean.parseBoolean(env("MIL_TEST_MYSQL_SSL", "true")), null);
        }
        return DatabaseSettings.sqlite(tempDir.resolve("test-" + (++counter) + ".db").toFile());
    }

    private static Connection raw(DatabaseSettings s) throws SQLException {
        if (s.mysql()) {
            return DriverManager.getConnection("jdbc:mysql://" + s.host() + ":" + s.port() + "/" + s.databaseName()
                    + "?useSSL=false&allowPublicKeyRetrieval=true", s.username(), s.password());
        }
        return DriverManager.getConnection("jdbc:sqlite:" + s.sqliteFile().getAbsolutePath());
    }

    /** Drops all tables and creates the 1.3 schema with one item, like a database written by 1.3. */
    private static void createV13Database(DatabaseSettings s) throws SQLException {
        try (Connection c = raw(s); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS co_list_item");
            st.execute("DROP TABLE IF EXISTS co_orders");
            st.execute("DROP TABLE IF EXISTS co_claim_log");
            if (s.mysql()) {
                st.execute("CREATE TABLE co_list_item (id INT AUTO_INCREMENT PRIMARY KEY, item_name VARCHAR(255), "
                        + "item_display VARCHAR(255), description TEXT, player VARCHAR(255), enable BOOLEAN, command TEXT, used INT)");
            } else {
                st.execute("CREATE TABLE co_list_item (id INTEGER PRIMARY KEY AUTOINCREMENT, item_name TEXT, "
                        + "item_display TEXT, description TEXT, player TEXT, enable INTEGER, command TEXT, used INTEGER)");
            }
            st.execute("INSERT INTO co_list_item (item_name, item_display, description, player, enable, command, used) "
                    + "VALUES ('Old', 'STONE', 'd', 'steve', 1, '/say <player> old', 1)");
        }
    }

    private CoDatabase open(DatabaseSettings s) {
        CoDatabase db = new CoDatabase(s, LOG);
        assertTrue(db.open(), "database must open");
        opened.add(db);
        return db;
    }

    @AfterEach
    void closeAll() {
        opened.forEach(CoDatabase::close);
        opened.clear();
    }

    private static List<ItemOwner> owner(String name, String uuid) {
        return List.of(new ItemOwner(name, uuid));
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("backends")
    void migratesV13DatabaseAndKeepsData(String backend) throws Exception {
        DatabaseSettings s = settings(backend);
        createV13Database(s);
        open(s).close();
        CoDatabase db = open(s); // second start: migration must be a no-op
        List<ItemData> items = db.getAllItems();
        assertEquals(1, items.size());
        assertEquals("steve", items.get(0).getPlayer());
        assertNull(items.get(0).getPlayerUuid());
        assertNull(items.get(0).getOrderId());
        assertTrue(items.get(0).isEnable());
        assertEquals(1, db.getEnabledItems(UUID_A, "Steve", "").size(), "unbound item visible by name, case-insensitive");
    }

    @Test
    void concurrentMigrationOfSharedMysqlDatabase() throws Exception {
        Assumptions.assumeTrue(mysqlConfigured(), "MySQL not configured");
        // Two servers sharing one MySQL database start at the same moment: both must succeed.
        for (int round = 0; round < 5; round++) {
            DatabaseSettings s = settings("mysql");
            createV13Database(s);
            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService ex = Executors.newFixedThreadPool(2);
            List<Future<Boolean>> results = new ArrayList<>();
            List<CoDatabase> dbs = List.of(new CoDatabase(s, LOG), new CoDatabase(s, LOG));
            for (CoDatabase db : dbs) {
                results.add(ex.submit(() -> {
                    barrier.await();
                    return db.open();
                }));
            }
            for (Future<Boolean> r : results) {
                assertTrue(r.get(), "both servers must open the database (round " + round + ")");
            }
            ex.shutdown();
            assertEquals(1, dbs.get(0).getAllItems().size());
            dbs.forEach(CoDatabase::close);
        }
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("backends")
    void orderIdIsIdempotentAlsoUnderConcurrency(String backend) throws Exception {
        DatabaseSettings s = settings(backend);
        createV13Database(s);
        CoDatabase db = open(s);

        CoDatabase.AddResult first = db.addItems("A", "STONE", "d", "say a", 1, owner("Steve", null), "ord-1", "add-item");
        assertFalse(first.replay());
        CoDatabase.AddResult again = db.addItems("A", "STONE", "d", "say a", 1, owner("Steve", null), "ord-1", "add-item");
        assertTrue(again.replay());
        assertEquals(1, again.count());

        List<Callable<CoDatabase.AddResult>> calls = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            calls.add(() -> db.addItems("C", "STONE", "d", "say c", 1,
                    List.of(new ItemOwner("X", null), new ItemOwner("Y", null)), "ord-2", "add-item-all"));
        }
        ExecutorService ex = Executors.newFixedThreadPool(8);
        int added = 0;
        for (Future<CoDatabase.AddResult> f : ex.invokeAll(calls)) {
            if (!f.get().replay()) {
                added++;
            }
        }
        ex.shutdown();
        assertEquals(1, added, "exactly one concurrent request with the same order id may add items");
        assertEquals(1 + 1 + 2, db.getAllItems().size());

        CoDatabase.AddResult empty = db.addItems("E", "STONE", "d", "say e", 1, List.of(), "ord-3", "add-item-online");
        assertFalse(empty.replay());
        assertTrue(db.addItems("E", "STONE", "d", "say e", 1, owner("Z", null), "ord-3", "add-item-online").replay());
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("backends")
    void bindingAndRenameAndOwners(String backend) throws Exception {
        DatabaseSettings s = settings(backend);
        createV13Database(s);
        CoDatabase db = open(s);
        db.addItems("B", "STONE", "d", "say b", 1, owner("OldName", UUID_A), null, "add-item");
        assertEquals(2, db.getAllOwners().size(), "steve (unbound) and OldName (bound)");

        assertEquals(1, db.bindPlayer(UUID_A, "Steve"), "binds the unbound 'steve' row by name");
        assertEquals(2, db.getItemsByUuid(UUID_A).size());
        assertTrue(db.getItemsByPlayer("OldName").isEmpty(), "bound row renamed to the current name");
        assertEquals(2, db.getItemsByPlayer("STEVE").size(), "name lookup is case-insensitive");
        assertEquals(1, db.getAllOwners().size());
        assertTrue(db.getEnabledItems(UUID_B, "Steve", "").isEmpty(), "another account with the same name sees nothing");
        assertEquals(2, db.getEnabledItems(UUID_A, "AnyName", "").size(), "listing by UUID ignores the name");

        // Case-only rename must also be stored (MySQL compares case-insensitively).
        db.bindPlayer(UUID_A, "STEVE");
        assertEquals("STEVE", db.getItemsByUuid(UUID_A).get(0).getPlayer());
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("backends")
    void claimIsAtomicLoggedAndUndoable(String backend) throws Exception {
        DatabaseSettings s = settings(backend);
        createV13Database(s);
        CoDatabase db = open(s);
        db.addItems("Reward", "STONE", "d", "/give <player> diamond 1", 1, owner("Steve", UUID_A), "ord-9", "add-item");
        int id = db.getItemsByUuid(UUID_A).get(0).getId();

        assertNull(db.claimItem(id, UUID_B, "Steve"), "another account cannot claim a bound item");

        List<Callable<CoDatabase.Claim>> calls = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            calls.add(() -> db.claimItem(id, UUID_A, "Steve"));
        }
        ExecutorService ex = Executors.newFixedThreadPool(8);
        CoDatabase.Claim claim = null;
        int ok = 0;
        for (Future<CoDatabase.Claim> f : ex.invokeAll(calls)) {
            if (f.get() != null) {
                ok++;
                claim = f.get();
            }
        }
        ex.shutdown();
        assertEquals(1, ok, "exactly one of 16 concurrent claims succeeds");
        assertNotNull(claim);
        assertEquals("give Steve diamond 1", claim.command());
        assertFalse(db.getItem(id).isEnable());
        assertEquals(0, db.getItem(id).getUsed());

        List<ClaimRecord> log = db.getClaims(null, UUID_A, "ord-9", null, 100);
        assertEquals(1, log.size());
        assertEquals("Steve", log.get(0).playerName());
        assertEquals("give Steve diamond 1", log.get(0).command());
        assertEquals(1, db.getClaims("steve", null, null, 0L, 100).size(), "player filter is case-insensitive");
        assertTrue(db.getClaims(null, null, null, System.currentTimeMillis() + 60_000, 100).isEmpty());

        db.undoClaim(id, claim.logId());
        assertTrue(db.getItem(id).isEnable(), "undo gives the item back");
        assertEquals(1, db.getItem(id).getUsed());
        assertTrue(db.getClaims(null, null, null, null, 100).isEmpty(), "undo removes the claim log row");
        assertNotNull(db.claimItem(id, UUID_A, "Steve"), "can be claimed again after undo");
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("backends")
    void claimsNewestFirstAndLimit(String backend) throws Exception {
        DatabaseSettings s = settings(backend);
        createV13Database(s);
        CoDatabase db = open(s);
        for (int i = 0; i < 5; i++) {
            db.addItems("I" + i, "STONE", "d", "say " + i, 1, owner("Steve", UUID_A), null, "add-item");
        }
        for (ItemData item : db.getItemsByUuid(UUID_A)) {
            assertNotNull(db.claimItem(item.getId(), UUID_A, "Steve"));
            Thread.sleep(5);
        }
        List<ClaimRecord> claims = db.getClaims(null, null, null, null, 3);
        assertEquals(3, claims.size());
        for (int i = 0; i < claims.size() - 1; i++) {
            assertTrue(claims.get(i).claimedAt() >= claims.get(i + 1).claimedAt());
        }
        assertEquals("I4", claims.get(0).itemName());
    }

    @Test
    void openFailsCleanlyWithoutDatabase() {
        // Unreachable MySQL: open() must return false quickly, not throw.
        DatabaseSettings s = new DatabaseSettings(true, "127.0.0.1", 1, "x", "x", "x", false, null);
        CoDatabase db = new CoDatabase(s, LOG);
        assertFalse(db.open());
        assertFalse(db.isOpen());
        db.close();
    }

    @Test
    void sqliteFolderIsCreated() {
        File f = tempDir.resolve("nested/dir/database.db").toFile();
        CoDatabase db = open(DatabaseSettings.sqlite(f));
        assertTrue(db.isOpen());
        assertTrue(f.exists());
    }
}
