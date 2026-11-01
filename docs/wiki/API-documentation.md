# MyItemLibrary API Documentation

The REST API lets external applications (webshops, bots, panels) add, list and delete library items.

## Table of Contents
1. [Authentication](#authentication)
2. [Base URL](#base-url)
3. [Responses](#responses)
4. [Endpoints](#endpoints)
   - [Get Player Items](#get-player-items)
   - [Add Item](#add-item)
   - [Add Item to All Players](#add-item-to-all-players)
   - [Add Item to Online Players](#add-item-to-online-players)
   - [Get All Items](#get-all-items)
   - [Get Specific Item](#get-specific-item)
   - [Delete Item](#delete-item)
5. [Item Fields](#item-fields)
6. [Error Handling](#error-handling)
7. [Rate Limiting and Size Limits](#rate-limiting-and-size-limits)
8. [Examples](#examples)

## Authentication

Every request must include the API key in the `X-API-Key` header:

```
X-API-Key: your_api_key_here
```

The key is in `plugins/MyItemLibrary/config.yml` (`c-api.c-api-key`). If it was empty, the plugin generated one when the API started. Requests without a key, with an empty key or with a wrong key get `401 Unauthorized`.

## Base URL

```
http://<host>:<port>
```

`<host>`/`<port>` come from `c-api-host` / `c-api-port` (default `127.0.0.1:1558`, so by default only the same machine can connect). See the [Configuration Guide](Configuration-guide#api-configuration).

## Responses

- Endpoints that return data respond with JSON (`Content-Type: application/json`).
- Endpoints that change data respond with a **plain-text** message (`Content-Type: text/plain`), e.g. `Item added successfully`.
- Errors respond with a plain-text message and an HTTP error code (see [Error Handling](#error-handling)).

## Endpoints

### Get Player Items

All items (claimed and unclaimed) of one player.

- **URL**: `/items/{playerName}`
- **Method**: `GET`
- **URL Params**: `playerName`: a valid Minecraft name (`A-Z a-z 0-9 _`, 1–16 characters, optional Bedrock/Floodgate prefix `.` or `*`)

**Response** `200`:
```json
[
  {
    "id": 1,
    "itemName": "&6Excalibur",
    "itemDisplay": "DIAMOND_SWORD",
    "description": "A legendary sword",
    "player": "Steve",
    "enable": true,
    "command": "give <player> diamond_sword 1",
    "used": 1
  }
]
```

`404` if the player has no items, `400` if the name is invalid.

### Add Item

Adds an item to one player's library.

- **URL**: `/add-item`
- **Method**: `POST`
- **Body** (JSON):
  ```json
  {
    "item_name": "&6Healing Apple",
    "item_display": "GOLDEN_APPLE",
    "description": "Restores health",
    "player": "Alex",
    "command": "give <player> golden_apple 1",
    "used": 1
  }
  ```

**Response** `200`: `Item added successfully`

### Add Item to All Players

Adds the item to every player that already has at least one item in the database.

- **URL**: `/add-item-all`
- **Method**: `POST`
- **Body**: same as [Add Item](#add-item) without `player`

**Response** `200`: `Item added for X players`

### Add Item to Online Players

Adds the item to every player that is online right now.

- **URL**: `/add-item-online`
- **Method**: `POST`
- **Body**: same as [Add Item](#add-item) without `player`

**Response** `200`: `Item added for X online players`

### Get All Items

- **URL**: `/items`
- **Method**: `GET`

**Response** `200`: array of items (same format as [Get Player Items](#get-player-items))

### Get Specific Item

- **URL**: `/item/{itemId}`
- **Method**: `GET`
- **URL Params**: `itemId`: positive integer

**Response** `200`: a single item object. `404` if not found.

### Delete Item

- **URL**: `/item/{itemId}`
- **Method**: `DELETE`
- **URL Params**: `itemId`: positive integer

**Response** `200`: `Item deleted successfully`. `404` if not found.

## Item Fields

| JSON field (request) | Response field | Required | Description |
|---|---|---|---|
| `item_name` | `itemName` | yes | Name shown in the GUI. Supports `&` color codes. Max 255 characters. |
| `item_display` | `itemDisplay` | yes | Item **material** shown in the GUI, e.g. `DIAMOND_SWORD`, `golden_apple`. Unknown materials are shown as a barrier. Max 255 characters. |
| `description` | `description` | yes (may be empty) | Lore. Use `\n` for new lines. Supports `&` color codes. Max 4096 characters. |
| `player` | `player` | `/add-item` only | Player name (see rules above). |
| `command` | `command` | yes | Console command run when the player claims the item. `<player>` is replaced with the player's name. A leading `/` is optional. Max 4096 characters. |
| `used` | `used` | yes | Integer ≥ 0, decreased by 1 when claimed. |
| – | `id` | – | Database ID. |
| – | `enable` | – | `true` = not claimed yet. Becomes `false` when claimed. |

> `<` and `>` in text fields are escaped (stored as `|lt|` / `|gt|`), except the `<player>` placeholder.

## Error Handling

| Code | Meaning |
|---|---|
| `400` | Bad Request: invalid JSON, missing/wrong field, invalid player name or ID. The body explains the problem, e.g. `Bad Request: Field 'command' is required and must be a string`. |
| `401` | Unauthorized: missing, empty or wrong `X-API-Key`. |
| `404` | Not Found: unknown path, item or player without items. |
| `405` | Method Not Allowed: e.g. `GET /add-item`. |
| `413` | Request Entity Too Large: body bigger than `max-request-size-bytes`. |
| `429` | Too Many Requests: rate limit exceeded. |
| `500` | Internal Server Error: e.g. database problem. Details are only written to the server console. |
| `503` | Service Unavailable: the server could not provide the online player list in time (`/add-item-online`). |

## Rate Limiting and Size Limits

- With `c-api-dos-protection.enabled: true` (default), each IP may send `max-requests-per-minute` requests (default 100) per `request-time-window-ms` (default 60 s). Further requests get `429`.
- Request bodies larger than `max-request-size-bytes` (default 1 MB) are rejected with `413`, also when sent without `Content-Length` (chunked).

See the [Configuration Guide](Configuration-guide#api-configuration).

## Examples

```bash
KEY="your_api_key_here"

# Give Steve 5 diamonds as a claimable reward
curl -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"item_name":"&b5 Diamonds","item_display":"DIAMOND","description":"Thanks for your purchase!","player":"Steve","command":"give <player> diamond 5","used":1}' \
  http://127.0.0.1:1558/add-item

# List Steve's items
curl -H "X-API-Key: $KEY" http://127.0.0.1:1558/items/Steve

# Delete item 12
curl -X DELETE -H "X-API-Key: $KEY" http://127.0.0.1:1558/item/12
```
