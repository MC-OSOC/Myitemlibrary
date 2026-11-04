package org.cakedek.myitemlibrary.database;

import java.io.File;

/**
 * Connection settings, read from config.yml by the plugin. Kept free of Bukkit types so the
 * database layer can be tested without a server.
 */
public record DatabaseSettings(boolean mysql, String host, int port, String username, String password,
                               String databaseName, boolean useSsl, File sqliteFile) {

    public static DatabaseSettings sqlite(File file) {
        return new DatabaseSettings(false, null, 0, null, null, null, false, file);
    }
}
