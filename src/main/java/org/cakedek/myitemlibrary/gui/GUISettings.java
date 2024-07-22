package org.cakedek.myitemlibrary.gui;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.cakedek.myitemlibrary.MyItemLibrary;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings({"CallToPrintStackTrace", "ResultOfMethodCallIgnored", "deprecation"})
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

        // เปิดใช้งาน API
        gui.setItem(2, createAPIToggleItem());

        // รีเซ็ตไฟล์ภาษา
        gui.setItem(3, createSettingsItem(Material.PAPER, "§fReset Language", "§a[Right click] §fReset language files to default"));

        gui.setItem(6, createSettingsItem(Material.PAPER, "§fMyItemLibrany", "Version" + plugin.getPluginVersion()));

        // นำไปใช้งานและเริ่มต้นใหม่
        gui.setItem(7,createSettingsItem(Material.EMERALD,
                plugin.getTranslation("settings.Apply_and_reload.name", player),
                plugin.getTranslation("settings.Apply_and_reload.description", player)));

        // ปุ่มกลับ
        gui.setItem(8, createSettingsItem(Material.REDSTONE,
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
        if (event.getSlot() == 2) {
            if (event.getClick() == ClickType.RIGHT) {
                toggleAPI(player);
            } else if (event.getClick() == ClickType.DROP) {  // 'R' key press
                generateNewAPIKey(player);
            }
        } else if (event.getClick() == ClickType.RIGHT || event.getSlot() == 8) {
            switch (event.getSlot()) {
                case 0: // Toggle auto-update
                    toggleAutoUpdate(player);
                    break;
                case 1: // Toggle debug mode
                    toggleDebugMode(player);
                    break;
                case 3: // รีเซ็ตไฟล์ภาษา
                    resetLanguageFiles(player);
                    break;
                case 7: // นำไปใช้งานและเริ่มต้นใหม่
                    applyAndReload(player);
                    break;
                case 8: // ปุ่มกลับที่คลิกช้ายได้
                    player.closeInventory();
                    plugin.getGuiOpen().openLibraryGui(player, 0);
                    break;
            }
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


    //  API
    private ItemStack createAPIToggleItem() {
        boolean isAPIEnabled = plugin.getConfig().getBoolean("c-api.c-api-enable", false);
        Material material = isAPIEnabled ? Material.LIME_DYE : Material.GRAY_DYE;
        String name = "§fREST API";

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add("§7API " + (isAPIEnabled ? "§aEnabled" : "§cDisabled"));
            if (isAPIEnabled) {
                String host = plugin.getConfig().getString("c-api.c-api-host", "0.0.0.0");
                int port = plugin.getConfig().getInt("c-api.c-api-port", 1558);
                lore.add("§7Address: §f" + host + ":" + port);
            }
            lore.add("");
            lore.add("§a[Right click]§f Enable or disable");
            lore.add("§a[Press Q]§f Generate new API key");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    private void toggleAPI(Player player) {
        boolean currentState = plugin.getConfig().getBoolean("c-api.c-api-enable", false);
        plugin.getConfig().set("c-api.c-api-enable", !currentState);
        plugin.saveConfig();

        if (!currentState) {
            plugin.getApi().startServer();
            player.sendMessage("§aAPI has been enabled.");
        } else {
            plugin.getApi().stopServer();
            player.sendMessage("§cAPI has been disabled.");
        }
        openSettingsGUI(player);
    }
    private void generateNewAPIKey(Player player) {
        String newApiKey = UUID.randomUUID().toString();
        plugin.getConfig().set("c-api.c-api-key", newApiKey);
        plugin.saveConfig();

        net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent(ChatColor.GREEN + "New API key generated: ");

        net.md_5.bungee.api.chat.TextComponent keyComponent = new net.md_5.bungee.api.chat.TextComponent(ChatColor.YELLOW + newApiKey);
        keyComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + newApiKey));
        keyComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Click to copy the API key to chat").create()));

        message.addExtra(keyComponent);

        player.spigot().sendMessage(message);

        player.sendMessage(ChatColor.GREEN + "API server restarted with new key. Changes are effective immediately.");
        player.sendMessage(ChatColor.GREEN + "Please update your applications with this new key.");

        plugin.reloadConfig();
        plugin.getApi().stopServer();
        plugin.getApi().startServer();

        openSettingsGUI(player);
    }

    // นำไปใช้งานและเริ่มต้นใหม่
    private void applyAndReload(Player player) {
        player.closeInventory();
        player.sendMessage(plugin.getTranslation("settings_messages.applying_changes", player));
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.reload();
            player.sendMessage(ChatColor.GREEN + ("RELOAD COMPLETE"));
        });
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
