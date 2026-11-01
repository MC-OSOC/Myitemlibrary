# Upgrading to 1.3

Version 1.3 updates the plugin for Minecraft 26.3, adds Paper support and fixes several security issues. Please read this before upgrading from 1.1.

## Requirements changed

- Minecraft **1.20.5 or newer** (up to 26.3), on Paper or Spigot.
- Java **21** (Minecraft 1.20.5–1.21.x) or Java **25** (Minecraft 26.x).
- Servers older than 1.20.5 are no longer supported. Use 1.1 for those.

## What you may need to change

1. **API key**: if `c-api-key` in your `config.yml` is empty or shorter than 16 characters, a new random key is generated and saved when the API starts. Update your webshop/applications with the key from `config.yml`.
2. **API host**: the default for new installs is `127.0.0.1`. Your existing `config.yml` is not changed: if it still says `0.0.0.0`, the API stays reachable from the network and the plugin prints a warning. Make sure the port is firewalled, or switch to `127.0.0.1`.
3. **Player names in `/add-item`**: must now be valid Minecraft names (`A-Z a-z 0-9 _`, max 16, optional `.`/`*` Floodgate prefix). Other names get `400`.
4. **Settings menu permission**: now `my_item_library.admin` (default: op) instead of an op check. Grant it with your permissions plugin if non-op staff should use it.
5. **"Apply and reload"** now reloads only MyItemLibrary instead of the whole server.
6. **Optional new setting**: `c-database.use-ssl` (default `false`) for encrypted MySQL connections.

Your database and existing items are kept. Items shown in a GUI that was open while upgrading cannot be clicked; reopen `/my-library`.

## Security fixes in 1.3

- The settings menu could be opened by any player through a chest renamed "Settings" in an anvil (allowed server reload, API toggle and reading the API key). **Fixed.**
- An empty API key accepted requests with an empty `X-API-Key` header. **Fixed** (key is required and auto-generated).
- Rewards could be claimed more than once and without checking the owner. **Fixed** (atomic claim in the database).
- Player names that act as command selectors (e.g. `@a` on offline-mode servers) were inserted into console commands. **Fixed.**
- Request size limit could be bypassed with chunked requests. **Fixed.**
- The API key could end up in the server log via a suggested chat command. **Fixed** (copy to clipboard).
- Internal error details are no longer returned by the API.
