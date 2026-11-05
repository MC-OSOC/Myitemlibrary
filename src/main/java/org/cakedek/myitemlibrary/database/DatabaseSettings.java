package org.cakedek.myitemlibrary.database;

import java.io.File;

/**
 * Connection settings, read from config.yml by the plugin. Kept free of Bukkit types so the
 * database layer can be tested without a server.
 *
 * @param allowPublicKeyRetrieval only used when {@code useSsl} is false: lets the MySQL driver fetch the server's
 *                                RSA key for the caching_sha2_password login (needed by MySQL 8 without SSL)
 */
public record DatabaseSettings(boolean mysql, String host, int port, String username, String password,
                               String databaseName, boolean useSsl, boolean allowPublicKeyRetrieval, File sqliteFile) {

    public static DatabaseSettings sqlite(File file) {
        return new DatabaseSettings(false, null, 0, null, null, null, false, false, file);
    }
}
