package org.cakedek.myitemlibrary;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.cakedek.myitemlibrary.api.Api;
import org.cakedek.myitemlibrary.commands.CommandDetails;
import org.cakedek.myitemlibrary.commands.CommandHandler;
import org.cakedek.myitemlibrary.config.ApiConfig;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.gui.GUIOpen;
import org.cakedek.myitemlibrary.gui.GUISettings;
import org.cakedek.myitemlibrary.config.PlayerConfig;

import java.io.File;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("ALL")
public final class MyItemLibrary extends JavaPlugin implements Listener {
    // Fields
    private PlayerConfig playerConfigHandler;
    private CoDatabase database;
    private CommandHandler commandHandler;
    private GUIOpen guiOpen;
    private GUISettings guiSettings;
    private Api api;
    private Map<ItemStack, CommandDetails> commandMap;
    private Map<String, YamlConfiguration> languageFiles;
    private Map<UUID, Boolean> playerSearchMode;

    private String pluginVersion;

    private boolean dosProtectionEnabled;
    private int maxRequestsPerMinute;
    private long requestTimeWindowMs;
    private int maxRequestSizeBytes;

    private ApiConfig apiConfig;


    // Plugin lifecycle methods
    @Override
    public void onEnable() {
        initializePlugin();
        setupCommands();
        registerEventListeners();
        loadDosProtectionConfig();
        this.pluginVersion = getDescription().getVersion();
        this.api = new Api(this);

        if (getConfig().getBoolean("c-api.c-api-enable", false)) {
            api.startServer();
        }

        if (!setupDatabase()) {
            getLogger().severe("Failed to setup database. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        api.stopServer();
    }

    // Initialization methods
    private void initializePlugin() {
        saveDefaultConfig();
        commandMap = new HashMap<>();
        languageFiles = new HashMap<>();
        playerSearchMode = new HashMap<>();
        playerConfigHandler = new PlayerConfig(this);

        loadLanguageFiles();

        if (!setupDatabase()) {
            getLogger().severe(getTranslation("messages.reload_fail"));
        } else {
            database.createTableIfNotExists();
        }

        commandHandler = new CommandHandler(this);
        guiOpen = new GUIOpen(this);
        guiSettings = new GUISettings(this);
        api = new Api(this);
    }

    private void setupCommands() {
        getCommand("my-library-reload").setExecutor(commandHandler);
        getCommand("my-library").setExecutor(guiOpen);
    }

    private void registerEventListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(guiOpen, this);
        guiSettings.registerEvents();
    }

    // Database methods
    public boolean setupDatabase() {
        String databaseMode = getConfig().getString("c-database-mode", "MySQL");

        if ("MySQL".equalsIgnoreCase(databaseMode)) {
            String host = getConfig().getString("c-database.host");
            int port = getConfig().getInt("c-database.port");
            String username = getConfig().getString("c-database.username");
            String password = getConfig().getString("c-database.password");
            String databaseName = getConfig().getString("c-database.database");

            if (host == null || username == null || password == null || databaseName == null) {
                getLogger().severe("MySQL database configuration is missing.");
                return false;
            }

            database = new CoDatabase(host, port, username, password, databaseName);
        } else if ("Local".equalsIgnoreCase(databaseMode)) {
            database = new CoDatabase(this);
        } else {
            getLogger().severe("Invalid database mode specified in config: " + databaseMode);
            return false;
        }

        return database.connect();
    }

    // Language methods
    public void loadLanguageFiles() {
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
            saveResource("lang/en_US.yml", false);
            saveResource("lang/th_TH.yml", false);
        }

        for (File file : Objects.requireNonNull(langFolder.listFiles())) {
            if (file.isFile() && file.getName().endsWith(".yml")) {
                String langCode = file.getName().replace(".yml", "");
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                languageFiles.put(langCode, config);
                getLogger().info("Loaded language file: " + langCode);
            }
        }
    }

    public void createCustomLanguageFile(String langCode) {
        File langFolder = new File(getDataFolder(), "lang");
        File customLangFile = new File(langFolder, langCode + ".yml");

        if (!customLangFile.exists()) {
            try {
                customLangFile.createNewFile();
                YamlConfiguration config = YamlConfiguration.loadConfiguration(customLangFile);

                YamlConfiguration defaultConfig = languageFiles.get("en_US");
                for (String key : defaultConfig.getKeys(true)) {
                    config.set(key, defaultConfig.get(key));
                }

                config.set("language.lang", langCode);
                config.save(customLangFile);

                languageFiles.put(langCode, config);
                getLogger().info("Created custom language file: " + langCode);
            } catch (IOException e) {
                getLogger().severe("Failed to create custom language file: " + langCode);
                e.printStackTrace();
            }
        } else {
            getLogger().warning("Custom language file already exists: " + langCode);
        }
    }

    public String getTranslation(String key, Object... args) {
        Player player = null;
        if (args.length > 0 && args[args.length - 1] instanceof Player) {
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
            return ChatColor.translateAlternateColorCodes('&', key);
        }

        String message = langConfig.getString(key, key);
        message = ChatColor.translateAlternateColorCodes('&', message);

        if (args.length > 0) {
            Object[] coloredArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof String) {
                    coloredArgs[i] = ChatColor.translateAlternateColorCodes('&', (String) args[i]);
                } else {
                    coloredArgs[i] = args[i];
                }
            }
            message = String.format(message, coloredArgs);
        }

        return message;
    }

    public Map<String, String> getAvailableLanguages() {
        Map<String, String> availableLanguages = new HashMap<>();
        for (Map.Entry<String, YamlConfiguration> entry : languageFiles.entrySet()) {
            String langCode = entry.getKey();
            YamlConfiguration langConfig = entry.getValue();
            String langName = langConfig.getString("language.lang", langCode);
            availableLanguages.put(langCode, langName);
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
        String currentLangCode = getPlayerLanguage(player);
        String[] availableLangCodes = languageFiles.keySet().toArray(new String[0]);
        int currentIndex = -1;
        for (int i = 0; i < availableLangCodes.length; i++) {
            if (availableLangCodes[i].equals(currentLangCode)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % availableLangCodes.length;
        String newLangCode = availableLangCodes[nextIndex];

        playerConfigHandler.setPlayerLanguage(player, newLangCode);
    }

    public String getPlayerLanguage(Player player) {
        if (player == null) {
            return getConfig().getString("default-language", "en_US");
        }
        return playerConfigHandler.getPlayerLanguage(player);
    }

    // API
    private void loadApiConfig() {
        FileConfiguration config = getConfig();
        this.apiConfig = new ApiConfig.Builder()
                .corsAllowOrigin(config.getString("c-cors.allow-origin", "*"))
                .corsAllowMethods(config.getString("c-cors.allow-methods", "GET,POST,PUT,DELETE,OPTIONS"))
                .corsAllowHeaders(config.getString("c-cors.allow-headers", "*"))
                .corsAllowCredentials(config.getBoolean("c-cors.allow-credentials", true))
                .corsMaxAge(config.getInt("c-cors.max-age", 1800))
                .contentTypeOptions(config.getString("c-security.content-type-options", "nosniff"))
                .strictTransportSecurity(config.getString("c-security.strict-transport-security", "max-age=31536000; includeSubDomains"))
                .build();
    }

    public ApiConfig getApiConfig() {
        return apiConfig;
    }

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

    public Map<ItemStack, CommandDetails> getCommandMap() {
        return commandMap;
    }

    public GUISettings getGuiSettings() {
        return guiSettings;
    }

    public GUIOpen getGuiOpen() {
        return guiOpen;
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

    public String getPluginVersion() {
        return pluginVersion;
    }

    // Player search mode methods
    public void setPlayerInSearchMode(UUID playerUUID, boolean inSearchMode) {
        playerSearchMode.put(playerUUID, inSearchMode);
    }

    public boolean isPlayerInSearchMode(UUID playerUUID) {
        return playerSearchMode.getOrDefault(playerUUID, false);
    }
}