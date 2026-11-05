package org.cakedek.myitemlibrary;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.commands.CommandHandler;
import org.cakedek.myitemlibrary.config.PlayerConfig;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.database.DatabaseSettings;
import org.cakedek.myitemlibrary.gui.GUIOpen;
import org.cakedek.myitemlibrary.gui.GUISettings;
import org.cakedek.myitemlibrary.gui.PaperChatListener;
import org.cakedek.myitemlibrary.gui.SpigotChatListener;
import org.cakedek.myitemlibrary.player.OnlinePlayers;
import org.cakedek.myitemlibrary.util.Platform;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MyItemLibrary extends JavaPlugin {
    public static final String PERMISSION_ADMIN = "my_item_library.admin";
    public static final String PERMISSION_RELOAD = "my_item_library.admin.reload";

    private PlayerConfig playerConfigHandler;
    private volatile CoDatabase database;
    private GUIOpen guiOpen;
    private GUISettings guiSettings;
    private Api api;
    private OnlinePlayers onlinePlayers;
    private final Map<String, YamlConfiguration> languageFiles = new LinkedHashMap<>();
    // Written from the async chat thread, so it must be thread-safe.
    private final Map<UUID, Boolean> playerSearchMode = new ConcurrentHashMap<>();

    private boolean dosProtectionEnabled;
    private int maxRequestsPerMinute;
    private long requestTimeWindowMs;
    private int maxRequestSizeBytes;

    // Plugin lifecycle methods
    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadDosProtectionConfig();

        playerConfigHandler = new PlayerConfig(this);
        loadLanguageFiles();

        onlinePlayers = new OnlinePlayers(this);
        onlinePlayers.registerOnlinePlayers();

        if (!setupDatabase()) {
            getLogger().severe("Failed to setup database. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        guiOpen = new GUIOpen(this);
        guiSettings = new GUISettings(this);
        api = new Api(this);

        CommandHandler commandHandler = new CommandHandler(this);
        getCommand("my-library-reload").setExecutor(commandHandler);
        getCommand("my-library").setExecutor(guiOpen);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(onlinePlayers, this);
        pm.registerEvents(guiOpen, this);
        pm.registerEvents(guiSettings, this);
        // Each platform gets its own chat listener; PaperChatListener is never loaded on Spigot.
        pm.registerEvents(Platform.PAPER ? new PaperChatListener(guiOpen) : new SpigotChatListener(guiOpen), this);
        getLogger().info("Running on " + Platform.name() + " " + getServer().getBukkitVersion());
        if (Long.getLong("myitemlibrary.test.claimDelayMs", 0L) > 0) {
            getLogger().warning("Test option myitemlibrary.test.claimDelayMs is set: claims are delayed. Do not use this in production.");
        }

        api.startServer();
        // Players already online (e.g. after /reload) get their items bound as if they had just joined.
        onlinePlayers.bindOnlinePlayers();
    }

    @Override
    public void onDisable() {
        if (api != null) {
            api.stopServer();
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }

    /**
     * Reloads config, languages, database and API. Returns false if the database could not be connected.
     */
    public boolean reloadPlugin() {
        reloadConfig();
        loadDosProtectionConfig();
        loadLanguageFiles();
        boolean ok = setupDatabase();
        if (api != null) {
            api.stopServer();
        }
        api = new Api(this);
        api.startServer();
        if (ok) {
            onlinePlayers.bindOnlinePlayers();
        }
        return ok;
    }

    // Database methods

    /**
     * Opens the connection pool and migrates the tables. Runs on the main thread, but only at startup and on
     * reload; all other database work runs on async tasks or API threads.
     */
    public synchronized boolean setupDatabase() {
        FileConfiguration config = getConfig();
        String databaseMode = config.getString("c-database-mode", "MySQL");

        DatabaseSettings settings;
        if ("MySQL".equalsIgnoreCase(databaseMode)) {
            String host = config.getString("c-database.host");
            String username = config.getString("c-database.username");
            String password = config.getString("c-database.password");
            String databaseName = config.getString("c-database.database");

            if (isBlank(host) || isBlank(username) || password == null || isBlank(databaseName)) {
                getLogger().severe("MySQL database configuration is missing.");
                return false;
            }
            settings = new DatabaseSettings(true, host, config.getInt("c-database.port", 3306), username, password,
                    databaseName, config.getBoolean("c-database.use-ssl", false), null);
        } else if ("Local".equalsIgnoreCase(databaseMode)) {
            settings = DatabaseSettings.sqlite(new File(getDataFolder(), "database.db"));
        } else {
            getLogger().severe("Invalid database mode specified in config: " + databaseMode);
            return false;
        }

        CoDatabase newDatabase = new CoDatabase(settings, getLogger());
        if (!newDatabase.open()) {
            return false;
        }

        // Close the previous pool so reloads do not leak connections.
        CoDatabase old = database;
        database = newDatabase;
        if (old != null) {
            old.close();
        }
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // Language methods
    public void loadLanguageFiles() {
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
            saveResource("lang/en_US.yml", false);
            saveResource("lang/th_TH.yml", false);
        }

        languageFiles.clear();
        File[] files = langFolder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".yml")) {
                String langCode = file.getName().substring(0, file.getName().length() - ".yml".length());
                languageFiles.put(langCode, YamlConfiguration.loadConfiguration(file));
                getLogger().info("Loaded language file: " + langCode);
            }
        }
    }

    /**
     * Looks up a translated message. If the last argument is a Player, that player's language is used.
     * Arguments are inserted as-is: color codes inside arguments (e.g. text typed by players) are not translated.
     */
    @SuppressWarnings("deprecation") // ChatColor is deprecated on Paper but is the only color API that also exists on Spigot
    public String getTranslation(String key, Object... args) {
        Player player = null;
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Player) {
            player = (Player) args[args.length - 1];
        }

        String langCode = getPlayerLanguage(player);
        YamlConfiguration langConfig = languageFiles.get(langCode);

        if (langConfig == null) {
            langCode = getConfig().getString("default-language", "en_US");
            langConfig = languageFiles.get(langCode);
        }

        if (langConfig == null) {
            getLogger().warning("Language file not found for " + langCode + ". Using hardcoded fallback.");
            return key;
        }

        String message = ChatColor.translateAlternateColorCodes('&', langConfig.getString(key, key));

        if (args != null && args.length > 0) {
            try {
                message = String.format(message, args);
            } catch (java.util.IllegalFormatException e) {
                getLogger().warning("Bad format string in language key " + key + ": " + e.getMessage());
            }
        }
        return message;
    }

    public Map<String, String> getAvailableLanguages() {
        Map<String, String> availableLanguages = new HashMap<>();
        for (Map.Entry<String, YamlConfiguration> entry : languageFiles.entrySet()) {
            availableLanguages.put(entry.getKey(), entry.getValue().getString("language.lang", entry.getKey()));
        }
        return availableLanguages;
    }

    public String getLanguageName(String langCode) {
        YamlConfiguration langConfig = languageFiles.get(langCode);
        if (langConfig != null) {
            return langConfig.getString("language.lang", langCode);
        }
        return langCode;
    }

    public void switchLanguage(Player player) {
        String[] availableLangCodes = languageFiles.keySet().toArray(new String[0]);
        if (availableLangCodes.length == 0) {
            return;
        }
        String currentLangCode = getPlayerLanguage(player);
        int currentIndex = -1;
        for (int i = 0; i < availableLangCodes.length; i++) {
            if (availableLangCodes[i].equals(currentLangCode)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % availableLangCodes.length;
        playerConfigHandler.setPlayerLanguage(player, availableLangCodes[nextIndex]);
    }

    public String getPlayerLanguage(Player player) {
        if (player == null) {
            return getConfig().getString("default-language", "en_US");
        }
        return playerConfigHandler.getPlayerLanguage(player);
    }

    // DoS protection
    private void loadDosProtectionConfig() {
        FileConfiguration config = getConfig();
        dosProtectionEnabled = config.getBoolean("c-api-dos-protection.enabled", true);
        maxRequestsPerMinute = config.getInt("c-api-dos-protection.max-requests-per-minute", 100);
        requestTimeWindowMs = config.getLong("c-api-dos-protection.request-time-window-ms", 60000);
        maxRequestSizeBytes = config.getInt("c-api-dos-protection.max-request-size-bytes", 1048576);

        if (maxRequestsPerMinute <= 0) {
            getLogger().warning("Invalid max-requests-per-minute value. Setting to default (100).");
            maxRequestsPerMinute = 100;
        }
        if (requestTimeWindowMs <= 0) {
            getLogger().warning("Invalid request-time-window-ms value. Setting to default (60000).");
            requestTimeWindowMs = 60000;
        }
        if (maxRequestSizeBytes <= 0) {
            getLogger().warning("Invalid max-request-size-bytes value. Setting to default (1048576).");
            maxRequestSizeBytes = 1048576;
        }
    }

    // Getters
    public CoDatabase getDatabase() {
        return database;
    }

    public GUISettings getGuiSettings() {
        return guiSettings;
    }

    public GUIOpen getGuiOpen() {
        return guiOpen;
    }

    public OnlinePlayers getOnlinePlayers() {
        return onlinePlayers;
    }

    public Api getApi() {
        return api;
    }

    public boolean isDosProtectionEnabled() {
        return dosProtectionEnabled;
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public long getRequestTimeWindowMs() {
        return requestTimeWindowMs;
    }

    public int getMaxRequestSizeBytes() {
        return maxRequestSizeBytes;
    }

    @SuppressWarnings("deprecation") // getDescription() is still the portable way across Spigot versions
    public String getPluginVersion() {
        return getDescription().getVersion();
    }

    // Player search mode methods
    public void setPlayerInSearchMode(UUID playerUUID, boolean inSearchMode) {
        if (inSearchMode) {
            playerSearchMode.put(playerUUID, true);
        } else {
            playerSearchMode.remove(playerUUID);
        }
    }

    public boolean isPlayerInSearchMode(UUID playerUUID) {
        return playerSearchMode.getOrDefault(playerUUID, false);
    }
}
