package org.cakedek.myitemlibrary.database;

/**
 * Who an item belongs to. The name is always set; the UUID (lower-case, with dashes) is null
 * while the item is not yet bound to an account (e.g. added by name for a player who has not joined).
 */
public record ItemOwner(String name, String uuid) {
}
