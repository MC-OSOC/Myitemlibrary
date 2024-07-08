package org.cakedek.myitemlibrary;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@SuppressWarnings("NullableProblems")
public class GuiHandler implements CommandExecutor, Listener {
    private final My_item_library plugin;
    private final CoDatabase database;
    private final Map<UUID, Integer> playerPageMap = new HashMap<>();

    public GuiHandler(My_item_library plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getTranslation("messages.players_only"));
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("my-library")) {
            openLibraryGui(player, 0);
            return true;
        }
        return false;
    }

    public void openLibraryGui(Player player, int page) {
        if (database != null) {
            try {
                ResultSet rs = database.getListItemsByPlayerAndEnabled(player.getName());
                List<ItemStack> items = new ArrayList<>();
                while (rs.next()) {
                    String itemName = rs.getString("item_name");
                    String itemDisplay = rs.getString("item_display");
                    String description = rs.getString("description");
                    String commandToRun = rs.getString("command");
                    int id = rs.getInt("id");

                    Material material;
                    try {
                        material = Material.valueOf(itemDisplay.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        material = Material.BARRIER;
                    }

                    ItemStack item = new ItemStack(material);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(itemName);
                    meta.setLore(Arrays.asList(description.split("\n")));
                    item.setItemMeta(meta);

                    items.add(item);
                    plugin.getCommandMap().put(item, new CommandDetails(id, commandToRun));
                }

                int itemsPerPage = 45;
                int totalPages = (int) Math.ceil(items.size() / (double) itemsPerPage);

                Inventory gui = Bukkit.createInventory(null, 54, plugin.getTranslation("gui.title", page + 1, player));

                int start = page * itemsPerPage;
                int end = Math.min(start + itemsPerPage, items.size());

                for (int i = start; i < end; i++) {
                    gui.setItem(i - start, items.get(i));
                }

                if (page > 0) {
                    ItemStack prev = new ItemStack(Material.ARROW);
                    ItemMeta prevMeta = prev.getItemMeta();
                    prevMeta.setDisplayName(plugin.getTranslation("gui.prev_page", player));
                    prev.setItemMeta(prevMeta);
                    gui.setItem(45, prev);
                }

                if (page < totalPages - 1) {
                    ItemStack next = new ItemStack(Material.ARROW);
                    ItemMeta nextMeta = next.getItemMeta();
                    nextMeta.setDisplayName(plugin.getTranslation("gui.next_page", player));
                    next.setItemMeta(nextMeta);
                    gui.setItem(53, next);
                }

                playerPageMap.put(player.getUniqueId(), page);
                player.openInventory(gui);
            } catch (SQLException e) {
                player.sendMessage(plugin.getTranslation("messages.db_error", player));
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred while fetching the item list.", e);
            }
        } else {
            player.sendMessage(plugin.getTranslation("messages.db_not_connected", player));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        Player player = (Player) event.getWhoClicked();
        CommandDetails commandDetails = plugin.getCommandMap().get(clickedItem);



        if (commandDetails != null) {
            player.closeInventory();
            String commandToRun = commandDetails.getCommand().replace("<player>", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
            String itemName = Objects.requireNonNull(clickedItem.getItemMeta()).getDisplayName();
            player.sendMessage(plugin.getTranslation("gui.item_received", itemName, player));

            try {
                database.updateItemEnabled(commandDetails.getId(), false);
                database.updateItemused(commandDetails.getId());
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred while updating the item.", e);
            }
        } else if (clickedItem.getType() == Material.ARROW) {
            int page = playerPageMap.get(player.getUniqueId());
            if (clickedItem.getItemMeta().getDisplayName().equalsIgnoreCase("Next Page >")) {
                openLibraryGui(player, page + 1);
            } else if (clickedItem.getItemMeta().getDisplayName().equalsIgnoreCase("< Previous Page")) {
                openLibraryGui(player, page - 1);
            }
        }
    }
}
