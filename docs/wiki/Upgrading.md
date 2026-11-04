# Upgrading

## 1.3 → 1.4

Replace the jar in `plugins/` and restart. No config changes are needed.

### Automatic database migration

On the first start, 1.4 changes the database by itself (MySQL and SQLite). Nothing is deleted:

- `co_list_item` gets two new, empty columns: `player_uuid` and `order_id` (plus indexes).
- New table `co_orders` (order ids already processed, for idempotent webshop requests).
- New table `co_claim_log` (who claimed what and when).

The server log shows `Database migration: added column co_list_item.player_uuid` etc. once. The migration is safe to run again and safe when several servers share one MySQL database.
**Make a backup of your database before upgrading.** After the migration, 1.3 still runs on the database (it ignores the new columns), but items claimed with 1.3 are not written to the claim log.

### Behaviour changes

1. **Items are bound to player UUIDs.** Existing items are stored by name and are bound to a UUID the next time that player joins. After that, a name change no longer loses unclaimed items. See [Players, Names and UUIDs](API-documentation#players-names-and-uuids).
2. **Name matching is case-insensitive everywhere.** On SQLite, 1.3 showed items stored as `steve` only to a player named exactly `steve`; 1.4 shows them to `Steve` too (as MySQL already did). This applies to the GUI, the join binding, `/items/{player}` and `/claims?player=`.
3. **Database work no longer runs on the server main thread.** Opening `/my-library` and claiming an item query the database in the background; the GUI opens a moment later. A slow or unreachable MySQL server no longer freezes the server (only startup and `/my-library-reload` still connect on the main thread).
4. **Connection pool.** MySQL uses a small HikariCP pool (bundled in the jar) instead of one shared connection; SQLite still uses a single connection. The `autoReconnect` JDBC option is no longer used, the pool replaces broken connections itself.
5. **Claim log.** Every claim is recorded in `co_claim_log` and can be read with `GET /claims`.
6. **New optional API fields**: `player_uuid` and `order_id`, and response header `X-Idempotent-Replay`. Existing webshop requests keep working unchanged.
7. **`/add-item-online`** no longer waits for the main thread, so it no longer returns `503` for a busy server. `503` now means the database is not connected.
8. **`/add-item-all`** gives one item per person: a player whose items are bound to a UUID counts once, even if they had several names.
9. If a player disconnects between clicking an item and the reward command running, the claim is undone and the item stays in their library.

## 1.1 → 1.3

Version 1.3 updates the plugin for Minecraft 26.3, adds Paper support and fixes several security issues. Please read this before upgrading from 1.1.

### Requirements changed

- Minecraft **1.20.5 or newer** (up to 26.3), on Paper or Spigot.
- Java **21** (Minecraft 1.20.5–1.21.x) or Java **25** (Minecraft 26.x).
- Servers older than 1.20.5 are no longer supported. Use 1.1 for those.

### What you may need to change

1. **API key**: if `c-api-key` in your `config.yml` is empty or shorter than 16 characters, a new random key is generated and saved when the API starts. Update your webshop/applications with the key from `config.yml`.
2. **API host**: the default for new installs is `127.0.0.1`. Your existing `config.yml` is not changed: if it still says `0.0.0.0`, the API stays reachable from the network and the plugin prints a warning. Make sure the port is firewalled, or switch to `127.0.0.1`.
3. **Player names in `/add-item`**: must now be valid Minecraft names (`A-Z a-z 0-9 _`, max 16, optional `.`/`*` Floodgate prefix). Other names get `400`.
4. **Settings menu permission**: now `my_item_library.admin` (default: op) instead of an op check. Grant it with your permissions plugin if non-op staff should use it.
5. **"Apply and reload"** now reloads only MyItemLibrary instead of the whole server.
6. **Optional new setting**: `c-database.use-ssl` (default `false`) for encrypted MySQL connections.

Your database and existing items are kept. Items shown in a GUI that was open while upgrading cannot be clicked; reopen `/my-library`.

### Security fixes in 1.3

- The settings menu could be opened by any player through a chest renamed "Settings" in an anvil (allowed server reload, API toggle and reading the API key). **Fixed.**
- An empty API key accepted requests with an empty `X-API-Key` header. **Fixed** (key is required and auto-generated).
- Rewards could be claimed more than once and without checking the owner. **Fixed** (atomic claim in the database).
- Player names that act as command selectors (e.g. `@a` on offline-mode servers) were inserted into console commands. **Fixed.**
- Request size limit could be bypassed with chunked requests. **Fixed.**
- The API key could end up in the server log via a suggested chat command. **Fixed** (copy to clipboard).
- Internal error details are no longer returned by the API.
