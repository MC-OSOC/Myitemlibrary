package org.cakedek.myitemlibrary.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.database.ItemData;
import org.cakedek.myitemlibrary.util.Input;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@SuppressWarnings("deprecation") // legacy String/ChatColor API is kept for Spigot compatibility
public class GUIOpen implements CommandExecutor, Listener {
    private static final int ITEMS_PER_PAGE = 45;
    private static final int INVENTORY_SIZE = 54;
    private static final int MAX_SEARCH_LENGTH = 64;

    private static final String BTN_PREV = "prev_page";
    private static final String BTN_NEXT = "next_page";
    private static final String BTN_SEARCH = "search";
    private static final String BTN_LANGUAGE = "language";
    private static final String BTN_SETTINGS = "settings";

    /**
     * Test only: waits this long between the database claim and running the reward, so CI can test a player
     * leaving mid-claim and the per-player claim guard. Off unless the JVM is started with
     * {@code -Dmyitemlibrary.test.claimDelayMs=N} (capped at 10 s).
     */
    private static final long TEST_CLAIM_DELAY_MS =
            Math.min(10_000L, Math.max(0L, Long.getLong("myitemlibrary.test.claimDelayMs", 0L)));

    private final MyItemLibrary plugin;
    private final NamespacedKey buttonKey;
    private final NamespacedKey itemIdKey;
    // Read from the async chat thread, so it must be thread-safe.
    private final Map<UUID, String> playerSearchMap = new ConcurrentHashMap<>();
    // Players whose claim is running on an async task; further clicks are ignored until it finished.
    private final Set<UUID> claimsInProgress = ConcurrentHashMap.newKeySet();

    public GUIOpen(MyItemLibrary plugin) {
        this.plugin = plugin;
        this.buttonKey = new NamespacedKey(plugin, "navigation_button");
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
    }

    /** Identifies our GUI and remembers who it was opened for and which page it shows. */
    private static final class LibraryGUIHolder implements InventoryHolder {
        private final UUID owner;
        private final int page;
        private Inventory inventory;

        private LibraryGUIHolder(UUID owner, int page) {
            this.owner = owner;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getTranslation("messages.players_only"));
            return true;
        }
        openLibraryGui((Player) sender, 0);
        return true;
    }

    /**
     * Main thread. Loads the player's items on an async task, then opens the GUI back on the main thread,
     * so a slow database never blocks the server.
     */
    public void openLibraryGui(Player player, int page) {
        CoDatabase database = plugin.getDatabase();
        if (database == null) {
            player.sendMessage(plugin.getTranslation("messages.db_not_connected", player));
            return;
        }

        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String searchTerm = playerSearchMap.getOrDefault(uuid, "");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ItemData> items;
            try {
                items = database.getEnabledItems(uuid.toString(), name, searchTerm);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "An error occurred while fetching the item list.", e);
                runOnMainThread(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getTranslation("messages.db_error", player));
                    }
                });
                return;
            }
            runOnMainThread(() -> {
                if (player.isOnline()) {
                    showLibraryGui(player, page, items);
                }
            });
        });
    }

    /** Schedules a task on the main thread unless the plugin was disabled in the meantime. */
    private void runOnMainThread(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void showLibraryGui(Player player, int page, List<ItemData> items) {
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        LibraryGUIHolder holder = new LibraryGUIHolder(player.getUniqueId(), page);
        Inventory gui = Bukkit.createInventory(holder, INVENTORY_SIZE, plugin.getTranslation("gui.title", page + 1, player));
        holder.inventory = gui;

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());
        for (int i = start; i < end; i++) {
            gui.setItem(i - start, createItemStack(items.get(i)));
        }

        if (page > 0) {
            gui.setItem(45, createButton(Material.ARROW, plugin.getTranslation("gui.prev_page", player), null, BTN_PREV));
        }
        if (page < totalPages - 1) {
            gui.setItem(53, createButton(Material.ARROW, plugin.getTranslation("gui.next_page", player), null, BTN_NEXT));
        }
        gui.setItem(49, createButton(Material.COMPASS, plugin.getTranslation("gui.search", player), createSearchLore(player), BTN_SEARCH));
        gui.setItem(50, createButton(Material.BOOK, plugin.getTranslation("gui.switch_language", player), createLanguageSwitchLore(player), BTN_LANGUAGE));
        if (player.hasPermission(MyItemLibrary.PERMISSION_ADMIN)) {
            gui.setItem(51, createButton(Material.REDSTONE, plugin.getTranslation("gui.settings.name", player),
                    Collections.singletonList(plugin.getTranslation("gui.settings.description", player)), BTN_SETTINGS));
        }

        player.openInventory(gui);
    }

    private ItemStack createItemStack(ItemData data) {
        Material material = data.getItemDisplay() == null ? null : Material.matchMaterial(data.getItemDisplay());
        if (material == null || !material.isItem() || material.isAir()) {
            material = Material.BARRIER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(data.getItemName()));
            meta.setLore(splitAndColorize(data.getDescription()));
            // The database id is stored on the item. The command is looked up again (and ownership checked)
            // when the item is clicked, so a copied/forged item cannot run anything.
            meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.INTEGER, data.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static List<String> splitAndColorize(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        for (String line : text.split("\n")) {
            lines.add(colorize(line));
        }
        return lines;
    }

    private ItemStack createButton(Material material, String displayName, List<String> lore, String type) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            if (lore != null) {
                meta.setLore(lore);
            }
            meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, type);
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

    private List<String> createLanguageSwitchLore(Player player) {
        List<String> lore = new ArrayList<>();
        String currentLang = plugin.getPlayerLanguage(player);
        for (Map.Entry<String, String> entry : plugin.getAvailableLanguages().entrySet()) {
            String prefix = entry.getKey().equals(currentLang) ? ChatColor.GREEN + "-> " + ChatColor.RESET : ChatColor.WHITE + "-  ";
            lore.add(prefix + entry.getValue());
        }
        return lore;
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LibraryGUIHolder)) {
            return;
        }
        // Block every interaction (shift-click, number keys, drops...) with our GUI.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        LibraryGUIHolder holder = (LibraryGUIHolder) event.getInventory().getHolder();

        // Only react to clicks in the top (GUI) inventory, not the player's own inventory,
        // and only for the player the GUI was opened for.
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()
                || !holder.owner.equals(player.getUniqueId())) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) {
            return;
        }
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String button = pdc.get(buttonKey, PersistentDataType.STRING);
        if (button != null) {
            handleButton(event, player, holder, button);
            return;
        }

        Integer itemId = pdc.get(itemIdKey, PersistentDataType.INTEGER);
        if (itemId != null) {
            handleItemClick(player, itemId, meta.getDisplayName());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LibraryGUIHolder) {
            event.setCancelled(true);
        }
    }

    private void handleButton(InventoryClickEvent event, Player player, LibraryGUIHolder holder, String button) {
        switch (button) {
            case BTN_NEXT:
                openLibraryGui(player, holder.page + 1);
                break;
            case BTN_PREV:
                openLibraryGui(player, holder.page - 1);
                break;
            case BTN_LANGUAGE:
                plugin.switchLanguage(player);
                openLibraryGui(player, holder.page);
                break;
            case BTN_SEARCH:
                if (event.isLeftClick()) {
                    player.closeInventory();
                    player.sendMessage(plugin.getTranslation("messages.enter_search_term", player));
                    plugin.setPlayerInSearchMode(player.getUniqueId(), true);
                } else if (event.isRightClick()) {
                    playerSearchMap.remove(player.getUniqueId());
                    openLibraryGui(player, 0);
                }
                break;
            case BTN_SETTINGS:
                if (player.hasPermission(MyItemLibrary.PERMISSION_ADMIN)) {
                    player.closeInventory();
                    plugin.getGuiSettings().openSettingsGUI(player);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Main thread. Claims the item on an async task (atomic claim + claim log in one transaction),
     * then runs the reward command back on the main thread.
     */
    private void handleItemClick(Player player, int itemId, String displayName) {
        CoDatabase database = plugin.getDatabase();
        if (database == null) {
            player.sendMessage(plugin.getTranslation("messages.db_not_connected", player));
            return;
        }

        // The player name is inserted into a console command. Refuse names that could act as
        // selectors or extra arguments (possible on offline-mode servers), e.g. "@a".
        if (!Input.isValidPlayerName(player.getName())) {
            plugin.getLogger().warning("Refusing to run reward command for unsafe player name: " + player.getName());
            player.closeInventory();
            return;
        }

        UUID uuid = player.getUniqueId();
        // Fast double clicks must not start a second claim while the first one is still running.
        if (!claimsInProgress.add(uuid)) {
            return;
        }
        player.closeInventory();

        String name = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CoDatabase.Claim claim;
            try {
                claim = database.claimItem(itemId, uuid.toString(), name);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "An error occurred while claiming item " + itemId, e);
                finishClaim(uuid, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getTranslation("messages.db_error", player));
                    }
                });
                return;
            }
            if (claim == null) {
                // Already claimed, or not this player's item.
                finishClaim(uuid, null);
                return;
            }
            if (TEST_CLAIM_DELAY_MS > 0) {
                try {
                    Thread.sleep(TEST_CLAIM_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            finishClaim(uuid, () -> runReward(player, database, itemId, claim, displayName));
        });
    }

    /** Back on the main thread: runs the follow-up (if any) and releases the per-player claim guard. */
    private void finishClaim(UUID uuid, Runnable onMainThread) {
        if (!plugin.isEnabled()) {
            claimsInProgress.remove(uuid);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (onMainThread != null) {
                    onMainThread.run();
                }
            } finally {
                claimsInProgress.remove(uuid);
            }
        });
    }

    private void runReward(Player player, CoDatabase database, int itemId, CoDatabase.Claim claim, String displayName) {
        if (!player.isOnline()) {
            // The player left between the click and now: give the item back instead of running the
            // command for someone who is not there (a "give" would be lost).
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    database.undoClaim(itemId, claim.logId());
                    plugin.getLogger().info(player.getName() + " left before the reward command ran; claim of item "
                            + itemId + " was undone");
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not undo claim of item " + itemId
                            + " for a player who left; the reward was not given", e);
                }
            });
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), claim.command());
        player.sendMessage(plugin.getTranslation("gui.item_received", displayName, player));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof LibraryGUIHolder) {
            HumanEntity human = event.getPlayer();
            if (plugin.isPlayerInSearchMode(human.getUniqueId())) {
                plugin.setPlayerInSearchMode(human.getUniqueId(), false);
                human.sendMessage(plugin.getTranslation("messages.search_cancelled", human));
            }
        }
    }

    /**
     * Called from the chat listener (Paper or Spigot, possibly on an async thread).
     * Returns true if the message was a search term and the chat event should be cancelled.
     */
    public boolean handleSearchInput(Player player, String message) {
        if (!plugin.isPlayerInSearchMode(player.getUniqueId())) {
            return false;
        }
        plugin.setPlayerInSearchMode(player.getUniqueId(), false);

        String raw = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', message == null ? "" : message));
        String searchTerm = raw == null ? "" : raw.trim();
        if (searchTerm.length() > MAX_SEARCH_LENGTH) {
            searchTerm = searchTerm.substring(0, MAX_SEARCH_LENGTH);
        }
        playerSearchMap.put(player.getUniqueId(), searchTerm);

        final String applied = searchTerm;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.sendMessage(plugin.getTranslation("messages.search_applied", applied, player));
            openLibraryGui(player, 0);
        });
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        playerSearchMap.remove(id);
        plugin.setPlayerInSearchMode(id, false);
        // Not the claim guard: an in-flight claim releases it itself (and undoes the claim, see runReward).
    }
}
