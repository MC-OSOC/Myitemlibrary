package org.cakedek.myitemlibrary.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cakedek.myitemlibrary.database.CoDatabase;
import org.cakedek.myitemlibrary.MyItemLibrary;

@SuppressWarnings("NullableProblems")
public class CommandHandler implements CommandExecutor {
    private final MyItemLibrary plugin;
    private final CoDatabase database;

    public CommandHandler(MyItemLibrary plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("my-library-reload")) {
            plugin.reloadConfig();
            if (!plugin.setupDatabase()) {
                sender.sendMessage("Database not configured properly.");
            } else {
                database.createTableIfNotExists();
                sender.sendMessage("Plugin reloaded and database connected.");
            }
            return true;
        }
        return false;
    }
}
