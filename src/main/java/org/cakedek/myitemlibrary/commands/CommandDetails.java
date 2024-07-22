package org.cakedek.myitemlibrary.commands;

public class CommandDetails {
    private final int id;
    private final String command;

    public CommandDetails(int id, String command) {
        this.id = id;
        this.command = command;
    }

    public int getId() {
        return id;
    }

    public String getCommand() {
        return command;
    }
}
