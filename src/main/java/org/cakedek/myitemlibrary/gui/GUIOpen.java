package org.cakedek.myitemlibrary.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.commands.CommandDetails;
import org.cakedek.myitemlibrary.MyItemLibrary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@SuppressWarnings("ALL")
public class GUIOpen implements CommandExecutor, Listener {
    private static final int ITEMS_PER_PAGE = 44;
    private static final int INVENTORY_SIZE = 54;

    private final MyItemLibrary plugin;
    private final CoDatabase database;
    private final Map<UUID, Integer> playerPageMap = new HashMap<>();
    private final Map<UUID, String> playerSearchMap = new HashMap<>();

    public GUIOpen(MyItemLibrary plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
    }

    @SuppressWarnings("NullableProblems")
    private static class LibraryGUIHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getTranslation("messages.players_only", sender));
            return true;
        }

        Player player = (Player) sender;

        // This will handle 'my-library', 'mylib', and 'ml'
        openLibraryGui(player, 0);
        return true;
    }


    public void openLibraryGui(Player player, int page) {
        if (database == null) {
            player.sendMessage(plugin.getTranslation("messages.db_not_connected", player));
            return;
        }

        try {
            String searchTerm = playerSearchMap.getOrDefault(player.getUniqueId(), "");
            ResultSet rs = database.getListItemsByPlayerAndEnabledAndSearch(player.getName(), searchTerm);
            List<ItemStack> items = loadItemsFromResultSet(rs);

            int totalPages = (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE);
            String guiTitle = plugin.getTranslation("gui.title", page + 1, player);
            Inventory gui = Bukkit.createInventory(new LibraryGUIHolder(), INVENTORY_SIZE, guiTitle);

            populateInventoryWithItems(gui, items, page);
            addNavigationButtons(gui, page, totalPages);
            addUtilityButtons(gui, player);

            playerPageMap.put(player.getUniqueId(), page);
            player.openInventory(gui);
        } catch (SQLException e) {
            player.sendMessage(plugin.getTranslation("messages.db_error", player));
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred while fetching the item list.", e);
        }
    }

    private List<ItemStack> loadItemsFromResultSet(ResultSet rs) throws SQLException {
        List<ItemStack> items = new ArrayList<>();
        while (rs.next()) {
            ItemStack item = createItemStackFromResultSet(rs);
            items.add(item);
            plugin.getCommandMap().put(item, new CommandDetails(rs.getInt("id"), rs.getString("command")));
        }
        return items;
    }

    private ItemStack createItemStackFromResultSet(ResultSet rs) throws SQLException {
        String itemName = rs.getString("item_name");
        String itemDisplay = rs.getString("item_display");
        String description = rs.getString("description");
        int id = rs.getInt("id");

        Material material = Material.BARRIER;
        try {
            material = Material.valueOf(itemDisplay.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(applyColorCodes(itemName));
            meta.setLore(splitAndApplyColorCodes(description));
            meta.setCustomModelData(id);
            item.setItemMeta(meta);
        }

        return item;
    }

    private String applyColorCodes(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private List<String> splitAndApplyColorCodes(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            lines.add(applyColorCodes(line));
        }
        return lines;
    }

    private void populateInventoryWithItems(Inventory gui, List<ItemStack> items, int page) {
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());

        for (int i = start; i < end; i++) {
            gui.setItem(i - start, items.get(i));
        }
    }

    private void addNavigationButtons(Inventory gui, int currentPage, int totalPages) {
        if (currentPage > 0) {
            gui.setItem(45, createNavigationItem(Material.ARROW, plugin.getTranslation("gui.prev_page", null), "prev_page"));
        }

        if (currentPage < totalPages - 1) {
            gui.setItem(53, createNavigationItem(Material.ARROW, plugin.getTranslation("gui.next_page", null), "next_page"));
        }
    }

    private void addUtilityButtons(Inventory gui, Player player) {
        gui.setItem(49, createSearchButton(player));
        gui.setItem(50, createLanguageSwitchButton(player));

        if (player.isOp()) {
            gui.setItem(51, createSettingsButton(player));
        }
    }

    private ItemStack createNavigationItem(Material material, String displayName, String type) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "navigation_button"),
                    PersistentDataType.STRING,
                    type
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSearchButton(Player player) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getTranslation("gui.search", player));
            meta.setLore(createSearchLore(player));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> createSearchLore(Player player) {
        List<String> lore = new ArrayList<>();
        String currentSearch = playerSearchMap.getOrDefault(player.getUniqueId(), "");

        lore.add(plugin.getTranslation("gui.current_search", currentSearch, player));
        lore.add(plugin.getTranslation("gui.left_click_to_search", player));
        lore.add(plugin.getTranslation("gui.right_click_to_clear", player));

        return lore;
    }

    private ItemStack createLanguageSwitchButton(Player player) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getTranslation("gui.switch_language", player));
            meta.setLore(createLanguageSwitchLore(player));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> createLanguageSwitchLore(Player player) {
        List<String> lore = new ArrayList<>();
        String currentLang = plugin.getPlayerLanguage(player);
        Map<String, String> availableLanguages = plugin.getAvailableLanguages();

        for (Map.Entry<String, String> entry : availableLanguages.entrySet()) {
            String langCode = entry.getKey();
            String langName = entry.getValue();
            String prefix = langCode.equals(currentLang) ? ChatColor.GREEN + "-> " + ChatColor.RESET : ChatColor.WHITE + "-  ";
            lore.add(prefix + langName);
        }

        return lore;
    }

    private ItemStack createSettingsButton(Player player) {
        ItemStack item = new ItemStack(Material.REDSTONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getTranslation("gui.settings.name", player));
            meta.setLore(Collections.singletonList(plugin.getTranslation("gui.settings.description", player)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleItemClick(InventoryClickEvent event, Player player, ItemStack clickedItem) {
        CommandDetails commandDetails = plugin.getCommandMap().get(clickedItem);

        if (commandDetails == null) {
            return;
        }

        player.closeInventory();
        String commandToRun = commandDetails.getCommand().replace("<player>", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
        player.sendMessage(plugin.getTranslation("gui.item_received", Objects.requireNonNull(clickedItem.getItemMeta()).getDisplayName(), player));

        updateItemInDatabase(commandDetails.getId());
    }

    private void updateItemInDatabase(int itemId) {
        try {
            database.updateItemEnabled(itemId, false);
            database.updateItemused(itemId);
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred while updating the item.", e);
        }
    }

    private void handleNavigationClick(InventoryClickEvent event, Player player) {
        int currentPage = playerPageMap.get(player.getUniqueId());
        ItemStack clickedItem = event.getCurrentItem();
        ItemMeta meta = clickedItem.getItemMeta();

        if (meta == null) {
            return;
        }

        NamespacedKey key = new NamespacedKey(plugin, "navigation_button");
        PersistentDataContainer container = meta.getPersistentDataContainer();

        if (container.has(key, PersistentDataType.STRING)) {
            String buttonType = container.get(key, PersistentDataType.STRING);
            if ("next_page".equals(buttonType)) {
                openLibraryGui(player, currentPage + 1);
            } else if ("prev_page".equals(buttonType)) {
                openLibraryGui(player, currentPage - 1);
            }
        }
    }
    private void handleSearchClick(Player player, InventoryClickEvent event) {
        if (event.isLeftClick()) {
            player.closeInventory();
            player.sendMessage(plugin.getTranslation("messages.enter_search_term", player));
            plugin.setPlayerInSearchMode(player.getUniqueId(), true);
        } else if (event.isRightClick()) {
            clearSearch(player);
            openLibraryGui(player, 0);
        }
    }

    private void clearSearch(Player player) {
        playerSearchMap.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LibraryGUIHolder)) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, "navigation_button");
            if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                handleNavigationClick(event, player);
                return;
            }
        }

        switch (clickedItem.getType()) {
            case BOOK:
                plugin.switchLanguage(player);
                openLibraryGui(player, playerPageMap.get(player.getUniqueId()));
                break;
            case COMPASS:
                handleSearchClick(player, event);
                break;
            case REDSTONE:
                if (player.isOp()) {
                    player.closeInventory();
                    GUISettings guiSettings = plugin.getGuiSettings();
                    guiSettings.openSettingsGUI(player);
                }
                break;
            default:
                handleItemClick(event, player, clickedItem);
                break;
        }
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof LibraryGUIHolder) {
            Player player = (Player) event.getPlayer();
            if (plugin.isPlayerInSearchMode(player.getUniqueId())) {
                plugin.setPlayerInSearchMode(player.getUniqueId(), false);
                player.sendMessage(plugin.getTranslation("messages.search_cancelled", player));
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.isPlayerInSearchMode(player.getUniqueId())) {
            event.setCancelled(true);
            String searchTerm = event.getMessage();
            playerSearchMap.put(player.getUniqueId(), searchTerm);
            plugin.setPlayerInSearchMode(player.getUniqueId(), false);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(plugin.getTranslation("messages.search_applied", searchTerm, player));
                openLibraryGui(player, 0);
            });
        }
    }
}