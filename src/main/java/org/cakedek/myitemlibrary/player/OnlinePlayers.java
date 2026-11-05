package org.cakedek.myitemlibrary.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.cakedek.myitemlibrary.MyItemLibrary;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.database.ItemOwner;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Thread-safe snapshot of the online players (name and UUID), updated by join/quit events on the main thread,
 * so API threads can look players up without touching the Bukkit API.
 * <p>
 * On join it also binds the player's not-yet-bound items to their UUID and updates the stored name of
 * items already bound to the UUID (renames), off the main thread.
 */
public class OnlinePlayers implements Listener {
    private final MyItemLibrary plugin;
    private final Map<String, ItemOwner> byLowerName = new ConcurrentHashMap<>();

    public OnlinePlayers(MyItemLibrary plugin) {
        this.plugin = plugin;
    }

    /** Main thread: registers players that are already online (plugin enable or reload). */
    public void registerOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            add(player);
        }
    }

    /** Main thread: binds the items of everyone online, e.g. after the database was (re)connected. */
    public void bindOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            bindAsync(ownerOf(player));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        ItemOwner owner = add(event.getPlayer());
        bindAsync(owner);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        byLowerName.remove(player.getName().toLowerCase(Locale.ROOT), ownerOf(player));
    }

    private ItemOwner add(Player player) {
        ItemOwner owner = ownerOf(player);
        byLowerName.put(player.getName().toLowerCase(Locale.ROOT), owner);
        return owner;
    }

    private static ItemOwner ownerOf(Player player) {
        return new ItemOwner(player.getName(), player.getUniqueId().toString());
    }

    private void bindAsync(ItemOwner owner) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CoDatabase database = plugin.getDatabase();
            if (database == null) {
                return;
            }
            try {
                int bound = database.bindPlayer(owner.uuid(), owner.name());
                if (bound > 0) {
                    plugin.getLogger().info("Bound " + bound + " item(s) to " + owner.name() + " (" + owner.uuid() + ")");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not bind items of " + owner.name() + " to their UUID", e);
            }
        });
    }

    /** Any thread: the online player with this name (case-insensitive), or null. */
    public ItemOwner find(String name) {
        return name == null ? null : byLowerName.get(name.toLowerCase(Locale.ROOT));
    }

    /** Any thread: everyone online. */
    public List<ItemOwner> all() {
        return new ArrayList<>(byLowerName.values());
    }
}
