![img-background-1.png](img-background-1.png)
[Thai Version](README_TH.md)
# MyItemLibrary
My library ready-to-use commands in a MySQL repository for Minecraft Server

## Features

- Custom item libraries for each player
- GUI-based item management system
- Multi-language support (currently English and Thai)
- MySQL (connection pool) and SQLite database support; database work runs off the server main thread
- Items are bound to the player's UUID, so name changes do not lose rewards
- RESTful API for external integrations, with idempotent `order_id`s for safe webshop retries
- Claim log (who claimed what and when)
- DoS protection for API endpoints

## Requirements

| Server | Minecraft | Java |
|---|---|---|
| **Paper** (and forks like Purpur) | 1.20.5 – **26.3** | 21 (1.20.5–1.21.x), 25 (26.x) |
| **Spigot** | 1.20.5 – **26.3** | 21 (1.20.5–1.21.x), 25 (26.x) |

One jar works on both. On Paper the plugin uses Paper's own chat event for search input.
Folia is not supported.

## Building

Requires **JDK 25** (paper-api 26.x is compiled for Java 25). The plugin itself is built as Java 21 bytecode so it still runs on 1.20.5+ servers.

```bash
mvn package
```

`mvn package` also runs the database tests on SQLite. To run them on MySQL too, set `MIL_TEST_MYSQL_HOST` (and optionally `MIL_TEST_MYSQL_PORT`, `_USER`, `_PASSWORD`, `_DATABASE`; defaults `3306`, `mil`, `milpass`, `mil`).

## Installation

1. Download the latest release of MyItemLibrary from the releases page.
2. Place the JAR file in your server's `plugins` folder.

## Configuration

After the first run, the plugin will generate a `config.yml` file in the `plugins/MyItemLibrary` folder. You can customize various settings:

- Database mode (MySQL or Local SQLite)
- MySQL connection details (if using MySQL mode)
- API settings (enable/disable, host, port, API key)
- DoS protection settings
- Default language

## Commands

- `/my-library` - Opens the item library GUI for the player
- `/my-library-reload` - Reloads the plugin configuration (requires `my_item_library.admin.reload` permission)

## API

MyItemLibrary provides a RESTful API for external integrations. The API endpoints include:

- GET `/items/{playerName}` or `/items/{uuid}` - Retrieve items for a specific player
- POST `/add-item` - Add an item to a player's library
- POST `/add-item-all` - Add an item to all players' libraries
- POST `/add-item-online` - Add an item to all online players' libraries
- GET `/items` - Retrieve all items in the database
- GET `/item/{itemId}` - Retrieve a specific item by ID
- DELETE `/item/{itemId}` - Delete a specific item by ID
- GET `/claims` - Claim log (filters: `player`, `uuid`, `order_id`, `since`, `limit`)

The add-item endpoints accept an optional `order_id`: repeating it adds nothing and returns `200` with header `X-Idempotent-Replay: true`.

API requests require an API key in the `X-API-Key` header.

Security notes:
- If `c-api-key` is empty or shorter than 16 characters, a random key is generated and saved to `config.yml` when the API starts.
- The API listens on `127.0.0.1` by default. Only use `0.0.0.0` behind a firewall.
- Request bodies are limited to `max-request-size-bytes`, and player names must be valid Minecraft names.

For detailed information on request parameters, response formats, and examples, please refer to our [API documentation](https://github.com/MC-OSOC/Myitemlibrary/wiki/API-documentation).

## Permissions

- `my_item_library.use` - Open your own library (default: everyone)
- `my_item_library.admin` - Settings menu: toggle API, generate API key, reload the plugin (default: op)
- `my_item_library.admin.reload` - Allows use of the `/my-library-reload` command (default: op)

## Documentation
Full documentation is in the [GitHub wiki](https://github.com/MC-OSOC/Myitemlibrary/wiki). The pages live in [`docs/wiki`](docs/wiki) and are published automatically.

## Support
For support, feature requests, or bug reports, please open an issue on the GitHub repository.