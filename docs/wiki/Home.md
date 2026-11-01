# Welcome to the MyItemLibrary Wiki!

## Overview

MyItemLibrary is a Minecraft server plugin that gives every player a personal **item library**: a GUI with rewards that run a console command when claimed. Items are added through the built-in REST API (for example from a webshop) and stored in MySQL or a local SQLite database.

**Latest version: 1.3** · Supports **Paper** and **Spigot** · Minecraft **1.20.5 – 26.3**

## Requirements

| Server | Minecraft | Java |
|---|---|---|
| Paper (and forks like Purpur) | 1.20.5 – 26.3 | 21 (1.20.5–1.21.x), 25 (26.x) |
| Spigot | 1.20.5 – 26.3 | 21 (1.20.5–1.21.x), 25 (26.x) |

The same jar works on Paper and Spigot. Folia is not supported.

## Key Features

- **GUI library per player**: claim rewards, search, page through items, switch language
- **Database support**: MySQL or local SQLite
- **Multi-language**: English and Thai included, add your own
- **REST API**: add, list and delete items from external systems (webshops, Discord bots, ...)
- **Secure by default**: API key required (auto-generated), localhost-only by default, rate limiting and request size limits, rewards can only be claimed once by their owner

## Getting Started

1. Download `my-item-library-<version>.jar` from the [Releases](https://github.com/MC-OSOC/Myitemlibrary/releases) page.
2. Put it in your server's `plugins` folder and start the server.
3. Edit `plugins/MyItemLibrary/config.yml` (see the [Configuration Guide](Configuration-guide)).
4. Players open their library with `/my-library`.

## Webshop Integration

1. Enable the API in `config.yml` (`c-api.c-api-enable: true`) and start the server. A strong API key is generated automatically and saved in `config.yml`.
2. From your webshop, call `POST /add-item` when a player buys something (see [API Documentation](API-documentation)).
3. The player opens `/my-library` and clicks the item; the plugin runs the command (e.g. `give <player> diamond 5`) from the console.

## Further Documentation

- [Configuration Guide](Configuration-guide)
- [API Documentation](API-documentation)
- [Command Reference](Command-reference)
- [Upgrading to 1.3](Upgrading)
