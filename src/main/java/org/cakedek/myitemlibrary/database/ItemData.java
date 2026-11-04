package org.cakedek.myitemlibrary.database;

/** One row of co_list_item. Serialized as-is (camelCase field names) by the REST API. */
public class ItemData {
    private final int id;
    private final String itemName;
    private final String itemDisplay;
    private final String description;
    private final String player;
    private final String playerUuid;
    private final boolean enable;
    private final String command;
    private final int used;
    private final String orderId;

    public ItemData(int id, String itemName, String itemDisplay, String description, String player, String playerUuid,
                    boolean enable, String command, int used, String orderId) {
        this.id = id;
        this.itemName = itemName;
        this.itemDisplay = itemDisplay;
        this.description = description;
        this.player = player;
        this.playerUuid = playerUuid;
        this.enable = enable;
        this.command = command;
        this.used = used;
        this.orderId = orderId;
    }

    public int getId() { return id; }
    public String getItemName() { return itemName; }
    public String getItemDisplay() { return itemDisplay; }
    public String getDescription() { return description; }
    public String getPlayer() { return player; }
    public String getPlayerUuid() { return playerUuid; }
    public boolean isEnable() { return enable; }
    public String getCommand() { return command; }
    public int getUsed() { return used; }
    public String getOrderId() { return orderId; }
}
