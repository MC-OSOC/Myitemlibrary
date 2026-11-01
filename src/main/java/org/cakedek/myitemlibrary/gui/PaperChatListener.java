package org.cakedek.myitemlibrary.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Search input on Paper, using Paper's own chat event.
 * Only loaded when {@link org.cakedek.myitemlibrary.util.Platform#PAPER} is true,
 * because the classes it uses do not exist on Spigot.
 */
public class PaperChatListener implements Listener {
    private final GUIOpen guiOpen;

    public PaperChatListener(GUIOpen guiOpen) {
        this.guiOpen = guiOpen;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (guiOpen.handleSearchInput(event.getPlayer(), text)) {
            event.setCancelled(true);
        }
    }
}
