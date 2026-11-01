package org.cakedek.myitemlibrary.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cakedek.myitemlibrary.MyItemLibrary;

public class CommandHandler implements CommandExecutor {
    private final MyItemLibrary plugin;

    public CommandHandler(MyItemLibrary plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("my-library-reload")) {
            return false;
        }
        if (!sender.hasPermission(MyItemLibrary.PERMISSION_RELOAD)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
            return true;
        }
        if (plugin.reloadPlugin()) {
            sender.sendMessage("Plugin reloaded and database connected.");
        } else {
            sender.sendMessage("Database not configured properly.");
        }
        return true;
    }
}
