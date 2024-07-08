package org.cakedek.myitemlibrary;

public class ItemData {
    private int id;
    private String itemName;
    private String itemDisplay;
    private String description;
    private String player;
    private boolean enable;
    private String command;
    private int used;

    public ItemData(int id, String itemName, String itemDisplay, String description, String player, boolean enable, String command, int used) {
        this.id = id;
        this.itemName = itemName;
        this.itemDisplay = itemDisplay;
        this.description = description;
        this.player = player;
        this.enable = enable;
        this.command = command;
        this.used = used;
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemDisplay() { return itemDisplay; }
    public void setItemDisplay(String itemDisplay) { this.itemDisplay = itemDisplay; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPlayer() { return player; }
    public void setPlayer(String player) { this.player = player; }
    public boolean isEnable() { return enable; }
    public void setEnable(boolean enable) { this.enable = enable; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public int getUsed() { return used; }
    public void setUsed(int used) { this.used = used; }
}