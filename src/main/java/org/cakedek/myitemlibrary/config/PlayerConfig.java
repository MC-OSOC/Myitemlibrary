package org.cakedek.myitemlibrary.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.cakedek.myitemlibrary.MyItemLibrary;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlayerConfig {
    private final MyItemLibrary plugin;
    private final File playerDataFolder;

    public PlayerConfig(MyItemLibrary plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "players");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
    }

    public FileConfiguration getPlayerConfig(UUID playerUUID) {
        File playerFile = new File(playerDataFolder, playerUUID.toString() + ".yml");
        if (!playerFile.exists()) {
            try {
                playerFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create player config for " + playerUUID);
                e.printStackTrace();
            }
        }
        return YamlConfiguration.loadConfiguration(playerFile);
    }

    public void savePlayerConfig(UUID playerUUID, FileConfiguration config) {
        File playerFile = new File(playerDataFolder, playerUUID.toString() + ".yml");
        try {
            config.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player config for " + playerUUID);
            e.printStackTrace();
        }
    }

    public String getPlayerLanguage(Player player) {
        FileConfiguration config = getPlayerConfig(player.getUniqueId());
        return config.getString("language", plugin.getConfig().getString("default-language", "en_US"));
    }

    public void setPlayerLanguage(Player player, String languageCode) {
        FileConfiguration config = getPlayerConfig(player.getUniqueId());
        config.set("language", languageCode);
        savePlayerConfig(player.getUniqueId(), config);
    }

}