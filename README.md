![img-background-1.png](img-background-1.png)
[Thai Version](README_TH.md)
# MyItemLibrary
My library ready-to-use commands in a MySQL repository for Minecraft Server

## Features

- Custom item libraries for each player
- GUI-based item management system
- Multi-language support (currently English and Thai)
- MySQL and SQLite database support
- RESTful API for external integrations
- DoS protection for API endpoints

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

- GET `/items/{playerName}` - Retrieve items for a specific player
- POST `/add-item` - Add an item to a player's library
- POST `/add-item-all` - Add an item to all players' libraries
- POST `/add-item-online` - Add an item to all online players' libraries
- GET `/items` - Retrieve all items in the database
- GET `/item/{itemId}` - Retrieve a specific item by ID
- DELETE `/item/{itemId}` - Delete a specific item by ID

API requests require an API key for authentication.

For detailed information on request parameters, response formats, and examples, please refer to our [API documentation](https://github.com/MC-OSOC/Myitemlibrary/wiki/API-documentation).

## Permissions

- `my_item_library.admin.reload` - Allows use of the `/my-library-reload` command

## Support
For support, feature requests, or bug reports, please open an issue on the GitHub repository.