package org.cakedek.myitemlibrary.database;

/** One row of co_claim_log. */
public record ClaimRecord(long id, int itemId, String playerUuid, String playerName, String itemName,
                          String command, String orderId, long claimedAt) {
}
