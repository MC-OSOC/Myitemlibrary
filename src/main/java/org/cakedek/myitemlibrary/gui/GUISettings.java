package org.cakedek.myitemlibrary.gui;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.api.Api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@SuppressWarnings("deprecation") // legacy String/ChatColor/BungeeCord chat API is kept for Spigot compatibility
public class GUISettings implements Listener {
    private static final int SLOT_API = 2;
    private static final int SLOT_RESET_LANG = 3;
    private static final int SLOT_VERSION = 6;
    private static final int SLOT_APPLY_RELOAD = 7;
    private static final int SLOT_BACK = 8;

    private final MyItemLibrary plugin;

    public GUISettings(MyItemLibrary plugin) {
        this.plugin = plugin;
    }

    /**
     * Identifies the settings GUI. Previously the GUI was recognised by its title ("Settings"),
     * so any player could rename a chest "Settings" in an anvil and use the admin buttons.
     */
    private static final class SettingsGUIHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void openSettingsGUI(Player player) {
        if (!player.hasPermission(MyItemLibrary.PERMISSION_ADMIN)) {
            return;
        }
        SettingsGUIHolder holder = new SettingsGUIHolder();
        Inventory gui = Bukkit.createInventory(holder, 9, plugin.getTranslation("settings.title", player));
        holder.inventory = gui;

        gui.setItem(SLOT_API, createAPIToggleItem());
        gui.setItem(SLOT_RESET_LANG, createSettingsItem(Material.PAPER, "§fReset Language", "§a[Right click] §fReset language files to default"));
        gui.setItem(SLOT_VERSION, createSettingsItem(Material.PAPER, "§fMyItemLibrary", "§aVersion §f" + plugin.getPluginVersion()));
        gui.setItem(SLOT_APPLY_RELOAD, createSettingsItem(Material.EMERALD,
                plugin.getTranslation("settings.Apply_and_reload.name", player),
                plugin.getTranslation("settings.Apply_and_reload.description", player)));
        gui.setItem(SLOT_BACK, createSettingsItem(Material.REDSTONE,
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SettingsGUIHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        // Re-check on every click: permissions may have been removed while the menu was open.
        if (!player.hasPermission(MyItemLibrary.PERMISSION_ADMIN)) {
            player.closeInventory();
            return;
        }
        // Ignore clicks in the player's own inventory.
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }

        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        if (slot == SLOT_API) {
            if (click == ClickType.RIGHT) {
                toggleAPI(player);
            } else if (click == ClickType.DROP) { // 'Q' key
                generateNewAPIKey(player);
            }
        } else if (slot == SLOT_BACK) {
            player.closeInventory();
            plugin.getGuiOpen().openLibraryGui(player, 0);
        } else if (click == ClickType.RIGHT) {
            if (slot == SLOT_RESET_LANG) {
                resetLanguageFiles(player);
            } else if (slot == SLOT_APPLY_RELOAD) {
                applyAndReload(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SettingsGUIHolder) {
            event.setCancelled(true);
        }
    }

    // API
    private ItemStack createAPIToggleItem() {
        boolean isAPIEnabled = plugin.getConfig().getBoolean("c-api.c-api-enable", false);
        ItemStack item = new ItemStack(isAPIEnabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fREST API");
            List<String> lore = new ArrayList<>();
            lore.add("§7API " + (isAPIEnabled ? "§aEnabled" : "§cDisabled"));
            if (isAPIEnabled) {
                String host = plugin.getConfig().getString("c-api.c-api-host", "127.0.0.1");
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

        Api api = plugin.getApi();
        if (!currentState) {
            api.startServer();
            player.sendMessage("§aAPI has been enabled.");
        } else {
            api.stopServer();
            player.sendMessage("§cAPI has been disabled.");
        }
        openSettingsGUI(player);
    }

    private void generateNewAPIKey(Player player) {
        String newApiKey = Api.generateApiKey();
        plugin.getConfig().set("c-api.c-api-key", newApiKey);
        plugin.saveConfig();

        TextComponent message = new TextComponent(ChatColor.GREEN + "New API key generated: ");
        TextComponent keyComponent = new TextComponent(ChatColor.YELLOW + newApiKey);
        // Copy to clipboard instead of suggesting it as a chat command: pressing Enter on a suggested
        // command would send the key to the server and write it to the server log.
        keyComponent.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, newApiKey));
        keyComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Click to copy the API key").create()));
        message.addExtra(keyComponent);
        player.spigot().sendMessage(message);

        // Restart only if the API is enabled (startServer checks the config).
        Api api = plugin.getApi();
        api.stopServer();
        api.startServer();

        player.sendMessage(ChatColor.GREEN + "The new key is effective immediately. Please update your applications.");
        openSettingsGUI(player);
    }

    /**
     * Reloads this plugin only (config, languages, database, API).
     * A full Bukkit.reload() is not supported on Paper and breaks many other plugins.
     */
    private void applyAndReload(Player player) {
        player.closeInventory();
        player.sendMessage(plugin.getTranslation("settings_messages.applying_changes", player));
        plugin.getLogger().info(player.getName() + " reloaded MyItemLibrary from the settings menu.");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.reloadPlugin()) {
                player.sendMessage(ChatColor.GREEN + "RELOAD COMPLETE");
            } else {
                player.sendMessage(plugin.getTranslation("messages.reload_fail", player));
            }
        });
    }

    private void resetLanguageFiles(Player player) {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        for (String langFile : new String[]{"en_US.yml", "th_TH.yml"}) {
            try (InputStream in = plugin.getResource("lang/" + langFile)) {
                if (in != null) {
                    Files.copy(in, new File(langFolder, langFile).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    player.sendMessage("Reset language file: " + langFile);
                }
            } catch (IOException e) {
                player.sendMessage("Error resetting language file: " + langFile);
                plugin.getLogger().log(Level.WARNING, "Error resetting language file " + langFile, e);
            }
        }

        plugin.loadLanguageFiles();
        player.sendMessage("All language files have been reset successfully");
    }
}
