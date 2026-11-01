# Command Reference

## Commands

| Command | Alias | Permission | Description |
|---|---|---|---|
| `/my-library` | `/mylibrary13` | `my_item_library.use` | Opens your item library GUI. Players only. |
| `/my-library-reload` | – | `my_item_library.admin.reload` | Reloads MyItemLibrary: `config.yml`, language files, database connection and REST API. Works from the console too. |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `my_item_library.use` | everyone | Open your own library. |
| `my_item_library.admin` | op | Shows the **Settings** button in the GUI and allows using the settings menu. Includes `my_item_library.admin.reload`. |
| `my_item_library.admin.reload` | op | Use `/my-library-reload`. |

## GUI

### Library (`/my-library`)

| Slot | Button | Action |
|---|---|---|
| Top 5 rows | Your items | **Click** to claim: the item's command is run from the console and the item is removed from your library. Each item can only be claimed once. |
| Bottom left | `<<< Previous Page` | Previous page |
| Bottom right | `Next Page >>>` | Next page |
| Compass | Search | **Left-click**, then type a search term in chat (searches name and description). **Right-click** clears the search. |
| Book | Switch Language | Cycles through the available languages (saved per player). |
| Redstone | Settings | Only with `my_item_library.admin`. Opens the settings menu. |

### Settings menu

| Button | Action |
|---|---|
| REST API (dye) | **Right-click**: enable/disable the API. **Q (drop key)**: generate a new API key; click the key in chat to copy it to the clipboard. |
| Reset Language | **Right-click**: restore `en_US.yml` and `th_TH.yml` to the defaults. |
| MyItemLibrary | Shows the plugin version. |
| Apply and reload | **Right-click**: reload MyItemLibrary (same as `/my-library-reload`). |
| Back | Return to the library. |
