package org.cakedek.myitemlibrary;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class My_item_library extends JavaPlugin implements Listener {
    private CoDatabase database;
    private CommandHandler commandHandler;
    private GuiHandler guiHandler;
    private ApiHandler apiHandler;
    private Map<ItemStack, CommandDetails> commandMap;
    private Map<String, YamlConfiguration> languageFiles;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        commandMap = new HashMap<>();
        languageFiles = new HashMap<>();

        loadLanguageFiles();

        if (!setupDatabase()) {
            getLogger().severe(getTranslation("messages.reload_fail"));
        } else {
            database.createTableIfNotExists();
        }
        getServer().getPluginManager().registerEvents(this, this);

        commandHandler = new CommandHandler(this);
        guiHandler = new GuiHandler(this);
        apiHandler = new ApiHandler(this);

        getCommand("my-library-reload").setExecutor(commandHandler);
        getCommand("my-library").setExecutor(guiHandler);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(guiHandler, this);

        apiHandler.startServer();
    }

    private void loadLanguageFiles() {
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
            saveResource("lang/en_US.yml", false);
            saveResource("lang/th_TH.yml", false);
        }

        for (File file : Objects.requireNonNull(langFolder.listFiles())) {
            if (file.isFile() && file.getName().endsWith(".yml")) {
                String lang = file.getName().replace(".yml", "");
                languageFiles.put(lang, YamlConfiguration.loadConfiguration(file));
            }
        }
    }

    public String getTranslation(String key, Object... args) {
        Player player = null;
        if (args.length > 0 && args[args.length - 1] instanceof Player) {
            player = (Player) args[args.length - 1];
        }

        String lang = player != null ? player.getLocale() : getConfig().getString("default-language", "en_US");
        YamlConfiguration langConfig = languageFiles.getOrDefault(lang, languageFiles.get("en_US"));

        String message = langConfig.getString(key, key);
        return String.format(message, args);
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        apiHandler.stopServer();
    }

    public boolean setupDatabase() {
        String host = getConfig().getString("c-database.host");
        int port = getConfig().getInt("c-database.port");
        String username = getConfig().getString("c-database.username");
        String password = getConfig().getString("c-database.password");
        String databaseName = getConfig().getString("c-database.database");

        if (host == null || username == null || password == null || databaseName == null) {
            getLogger().severe("Database configuration is missing.");
            return false;
        }

        database = new CoDatabase(host, port, username, password, databaseName);
        return database.connect();
    }

    public CoDatabase getDatabase() {
        return database;
    }

    public Map<ItemStack, CommandDetails> getCommandMap() {
        return commandMap;
    }
}
