package org.cakedek.myitemlibrary;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class GUISettings implements Listener {
    private final MyItemLibrary plugin;
    private boolean isRegistered = false;

    public GUISettings(MyItemLibrary plugin) {
        this.plugin = plugin;
    }

    public void registerEvents() {
        if (!isRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            isRegistered = true;
        }
    }

    public void openSettingsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, plugin.getTranslation("settings.title", player));

        // Toggle auto-update
        gui.setItem(0, createSettingsItem(Material.REDSTONE_TORCH, "Auto Update", "Toggle automatic updates"));

        // Toggle debug mode
        gui.setItem(1, createSettingsItem(Material.COMMAND_BLOCK, "Debug Mode", "Toggle debug mode"));

        // Language settings
        gui.setItem(2, createSettingsItem(Material.BOOK, "Language", "Change language"));

        // Reset Language
        gui.setItem(3, createSettingsItem(Material.PAPER, "Reset Language", "Reset language files to default"));

        // Back button
        gui.setItem(8, createSettingsItem(Material.REDSTONE_TORCH,
                plugin.getTranslation("settings.back.name", player),
                plugin.getTranslation("settings.back.description", player)));

        player.openInventory(gui);
    }

    private ItemStack createSettingsItem(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add(description);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(plugin.getTranslation("settings.title", event.getWhoClicked()))) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null) {
            return;
        }

        switch (event.getSlot()) {
            case 0: // Toggle auto-update
                toggleAutoUpdate(player);
                break;
            case 1: // Toggle debug mode
                toggleDebugMode(player);
                break;
            case 2: // Language settings
                openLanguageSettings(player);
                break;
            case 3: // Reset Language
                resetLanguageFiles(player);
                break;
            case 8: // Back button
                player.closeInventory();
                plugin.getGuiOpen().openLibraryGui(player, 0);
                break;
        }
    }


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void toggleAutoUpdate(Player player) {
        boolean currentState = plugin.getConfig().getBoolean("auto-update", true);
        plugin.getConfig().set("auto-update", !currentState);
        plugin.saveConfig();
        player.sendMessage("Auto-update has been " + (!currentState ? "enabled" : "disabled"));
    }

    private void toggleDebugMode(Player player) {
        boolean currentState = plugin.getConfig().getBoolean("debug-mode", false);
        plugin.getConfig().set("debug-mode", !currentState);
        plugin.saveConfig();
        player.sendMessage("Debug mode has been " + (!currentState ? "enabled" : "disabled"));
    }

    private void openLanguageSettings(Player player) {
        player.closeInventory();
        plugin.switchLanguage(player);
        player.sendMessage("Language has been changed");
    }


    // ส่วนการรีเซ็ตภาษา
    private void resetLanguageFiles(Player player) {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        String[] defaultLangs = {"en_US.yml", "th_TH.yml"};
        for (String langFile : defaultLangs) {
            try (InputStream in = plugin.getResource("lang/" + langFile)) {
                if (in != null) {
                    File outFile = new File(langFolder, langFile);
                    Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    player.sendMessage("Reset language file: " + langFile);
                }
            } catch (IOException e) {
                player.sendMessage("Error resetting language file: " + langFile);
                e.printStackTrace();
            }
        }

        // Reload language files
        plugin.loadLanguageFiles();
        player.sendMessage("All language files have been reset successfully");
    }
}
