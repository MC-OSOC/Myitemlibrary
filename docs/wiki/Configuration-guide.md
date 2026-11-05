# MyItemLibrary Configuration Guide

The main configuration file is `plugins/MyItemLibrary/config.yml`. It is created on the first start. Existing files are never overwritten by updates, so compare with the defaults below after upgrading.

## Table of Contents
1. [Database Configuration](#database-configuration)
2. [API Configuration](#api-configuration)
3. [Language Settings](#language-settings)
4. [Saving Changes](#saving-changes)

## Database Configuration

```yaml
c-database-mode: Local  # "Local" (SQLite) or "MySQL"

# MySQL Settings (MySQL Mode Only)
c-database:
  host: "localhost"
  port: 3306
  username: ""
  password: ""
  database: ""
  # Encrypt the MySQL connection (recommended when the database is on another machine)
  use-ssl: false
```

- **Local**: an SQLite file `database.db` is created in the plugin folder. Nothing else to configure.
- **MySQL**: fill in `c-database`. MariaDB works too (use MySQL mode; tested with MariaDB 11.8 and MySQL 8.4). The tables (`co_list_item`, `co_orders`, `co_claim_log`) are created and migrated automatically. MySQL uses a small connection pool (HikariCP, bundled in the jar, max 6 connections).
- `use-ssl`: set to `true` when the database is not on the same machine. Your MySQL server must have SSL enabled.
- If a connection drops (for example MySQL `wait_timeout`), the pool replaces it automatically.
- Database work (opening the GUI, claiming, API requests) runs off the server main thread, so a slow database does not freeze the server.

## API Configuration

```yaml
c-api:
  c-api-enable: false
  # 127.0.0.1 = only this machine can call the API. Use 0.0.0.0 only behind a firewall.
  c-api-host: 127.0.0.1
  c-api-port: 1558
  # Leave empty to auto-generate a strong key when the API starts.
  c-api-key: ""

# DoS Protection Settings
c-api-dos-protection:
  enabled: true
  max-requests-per-minute: 100
  request-time-window-ms: 60000
  max-request-size-bytes: 1048576  # 1 MB
```

- `c-api-enable`: set to `true` to start the API. You can also toggle it in-game from the settings menu.
- `c-api-host`:
  - `127.0.0.1` (default): only programs on the same machine can connect. Use this when your webshop runs on the same server, or put a reverse proxy (nginx, Caddy) with HTTPS in front.
  - `0.0.0.0`: listens on all network interfaces. **Only use this behind a firewall** that allows just your webshop's IP. The plugin prints a warning at startup.
- `c-api-key`: must be at least 16 characters. If it is empty or shorter, the plugin generates a random 43-character key and saves it here when the API starts. You can also generate a new key in-game (settings menu → REST API → press **Q**).
- `c-api-dos-protection`:
  - `enabled`: per-IP rate limiting.
  - `max-requests-per-minute` / `request-time-window-ms`: how many requests one IP may send per time window.
  - `max-request-size-bytes`: maximum request body size. This limit is always enforced, also for chunked requests.

> The API uses plain HTTP. If it must be reachable over the internet, put it behind a reverse proxy with HTTPS so the API key is not sent in clear text.

## Language Settings

```yaml
default-language: en_US
```

Included languages: `en_US`, `th_TH`. Players can switch language in the GUI (book icon).

To add a language:
1. Copy `plugins/MyItemLibrary/lang/en_US.yml` to a new file, e.g. `fr_FR.yml`.
2. Translate the messages and set `language.lang` to the language name (e.g. `"Français"`).
3. Run `/my-library-reload`. Optionally set `default-language: fr_FR`.

Color codes use `&` (e.g. `&a`). Keep `%s` / `%d` placeholders in the translated text.

## Saving Changes

After editing `config.yml`:
1. Save the file.
2. Run `/my-library-reload` (permission `my_item_library.admin.reload`), or use **Apply and reload** in the in-game settings menu. This reloads only MyItemLibrary (config, languages, database, API), not the whole server.

Back up your configuration before major changes.
