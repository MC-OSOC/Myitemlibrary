package org.cakedek.myitemlibrary.util;

/** Detects whether the server runs Paper (or a fork such as Purpur) or plain Spigot. */
public final class Platform {
    public static final boolean PAPER = classExists("io.papermc.paper.event.player.AsyncChatEvent");

    private Platform() {
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, Platform.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String name() {
        return PAPER ? "Paper" : "Spigot";
    }
}
