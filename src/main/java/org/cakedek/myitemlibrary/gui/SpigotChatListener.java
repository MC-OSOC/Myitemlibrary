package org.cakedek.myitemlibrary.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** Search input on Spigot. Paper uses {@link PaperChatListener} instead (this event is deprecated there). */
@SuppressWarnings("deprecation")
public class SpigotChatListener implements Listener {
    private final GUIOpen guiOpen;

    public SpigotChatListener(GUIOpen guiOpen) {
        this.guiOpen = guiOpen;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (guiOpen.handleSearchInput(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }
}
