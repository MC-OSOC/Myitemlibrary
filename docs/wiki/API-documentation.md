# MyItemLibrary API Documentation

The REST API lets external applications (webshops, bots, panels) add, list and delete library items, and read the claim log.

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
   - [Get Claim Log](#get-claim-log)
5. [Item Fields](#item-fields)
6. [Players, Names and UUIDs](#players-names-and-uuids)
7. [Idempotent Orders (order_id)](#idempotent-orders-order_id)
8. [Error Handling](#error-handling)
9. [Rate Limiting and Size Limits](#rate-limiting-and-size-limits)
10. [Examples](#examples)

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
- A repeated `order_id` responds `200` with the header `X-Idempotent-Replay: true` (see [Idempotent Orders](#idempotent-orders-order_id)).
- Errors respond with a plain-text message and an HTTP error code (see [Error Handling](#error-handling)).

## Endpoints

### Get Player Items

All items (claimed and unclaimed) of one player.

- **URL**: `/items/{playerName}` or `/items/{uuid}`
- **Method**: `GET`
- **URL Params**: either
  - `playerName`: a valid Minecraft name (`A-Z a-z 0-9 _`, 1–16 characters, optional Bedrock/Floodgate prefix `.` or `*`). Matched case-insensitively against the stored name.
  - `uuid`: the player's UUID (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`). Returns the items bound to that UUID.

**Response** `200`:
```json
[
  {
    "id": 1,
    "itemName": "&6Excalibur",
    "itemDisplay": "DIAMOND_SWORD",
    "description": "A legendary sword",
    "player": "Steve",
    "playerUuid": "8667ba71-b85a-4004-af54-457a9734eed7",
    "enable": true,
    "command": "give <player> diamond_sword 1",
    "used": 1,
    "orderId": "shop-1001"
  }
]
```

`playerUuid` is `null` while the item is not bound to an account yet, `orderId` is `null` for items added without `order_id`.
`404` if the player has no items, `400` if the name/UUID is invalid.

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
    "player_uuid": "c06f8906-4c8a-4911-9c29-ea1dbd1aab82",
    "command": "give <player> golden_apple 1",
    "used": 1,
    "order_id": "shop-1002"
  }
  ```
  `player_uuid` and `order_id` are optional.

**Response** `200`: `Item added successfully`
(or, if `order_id` was already used: `Order shop-1002 was already processed (1 item(s)); nothing was added` with header `X-Idempotent-Replay: true`)

### Add Item to All Players

Adds the item to every player that already has at least one item in the database.

- **URL**: `/add-item-all`
- **Method**: `POST`
- **Body**: same as [Add Item](#add-item) without `player` / `player_uuid`. `order_id` is optional.

One item per person: players whose items are bound to a UUID get the item bound to that UUID (under their newest name), unbound names get it by name.

**Response** `200`: `Item added for X players`

### Add Item to Online Players

Adds the item to every player that is online right now.

- **URL**: `/add-item-online`
- **Method**: `POST`
- **Body**: same as [Add Item](#add-item) without `player` / `player_uuid`. `order_id` is optional.

The items are bound to the online players' UUIDs.

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

### Get Claim Log

Every claim (a player clicked an item in `/my-library` and the reward command ran), newest first.

- **URL**: `/claims`
- **Method**: `GET`
- **Query parameters** (all optional, each at most once; unknown parameters are rejected):

| Parameter | Description |
|---|---|
| `player` | Player name at the time of the claim (valid Minecraft name, case-insensitive) |
| `uuid` | Player UUID |
| `order_id` | Order id of the claimed item (same format as `order_id` in the body) |
| `since` | Only claims at or after this time, epoch **milliseconds** (integer ≥ 0) |
| `limit` | Maximum number of rows, 1–1000, default 100 |

**Response** `200`:
```json
[
  {
    "id": 7,
    "item_id": 42,
    "player_uuid": "8667ba71-b85a-4004-af54-457a9734eed7",
    "player_name": "Steve",
    "item_name": "&b5 Diamonds",
    "command": "give Steve diamond 5",
    "order_id": "shop-1001",
    "claimed_at": 1793819892770
  }
]
```

`command` is the command that was run (`<player>` already replaced). `order_id` is `null` for items added without one. An empty result is `[]`. Invalid parameters get `400`.

## Item Fields

| JSON field (request) | Response field | Required | Description |
|---|---|---|---|
| `item_name` | `itemName` | yes | Name shown in the GUI. Supports `&` color codes. Max 255 characters. |
| `item_display` | `itemDisplay` | yes | Item **material** shown in the GUI, e.g. `DIAMOND_SWORD`, `golden_apple`. Unknown materials are shown as a barrier. Max 255 characters. |
| `description` | `description` | yes (may be empty) | Lore. Use `\n` for new lines. Supports `&` color codes. Max 4096 characters. |
| `player` | `player` | `/add-item` only | Player name (see rules above). For bound items the stored name is updated when the player joins with a new name. |
| `player_uuid` | `playerUuid` | no (`/add-item` only) | Player UUID, `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`. Stored lower-case. See [Players, Names and UUIDs](#players-names-and-uuids). |
| `order_id` | `orderId` | no | Your order/transaction id, 1–64 characters of `A-Z a-z 0-9 . _ : -`. See [Idempotent Orders](#idempotent-orders-order_id). |
| `command` | `command` | yes | Console command run when the player claims the item. `<player>` is replaced with the player's name. A leading `/` is optional. Max 4096 characters. |
| `used` | `used` | yes | Integer ≥ 0, decreased by 1 when claimed. |
| – | `id` | – | Database ID. |
| – | `enable` | – | `true` = not claimed yet. Becomes `false` when claimed. |

> `<` and `>` in text fields are escaped (stored as `|lt|` / `|gt|`), except the `<player>` placeholder.

## Players, Names and UUIDs

Since 1.4 items are bound to the player's **UUID**, so a name change no longer loses unclaimed items.

- Send `player_uuid` if your webshop knows it. Otherwise send only `player`: if that player is online, the plugin uses their UUID; if not, the item is stored by name (no Mojang lookup, the request never waits on it).
- When a player joins, every item that is not bound yet and whose name matches (case-insensitive) is bound to that player's UUID, and items already bound to the UUID get the player's current name.
- The library GUI shows a player the items bound to their UUID plus not-yet-bound items with their name.
- `<player>` in the command is replaced with the player's **current** name when they claim.

Name matching is case-insensitive on both MySQL and SQLite (`steve` and `Steve` are the same player).

## Idempotent Orders (order_id)

Webshops often retry a request when a response gets lost. Send a unique `order_id` with `/add-item`, `/add-item-all` or `/add-item-online` so a retry never gives the reward twice:

- The first request with an `order_id` adds the items and records the order, in one database transaction.
- Any later request with the same `order_id` adds **nothing** and responds `200` with header `X-Idempotent-Replay: true` and a message such as `Order shop-1001 was already processed (1 item(s)); nothing was added`. Treat this as success.
- This also holds for concurrent duplicates: the order id is the primary key of the `co_orders` table, so only one request can insert it.
- Order ids are global across the three endpoints. An order is recorded even if it reached 0 players (e.g. `/add-item-online` with nobody online).
- The `order_id` is stored on each item (`orderId`) and in the claim log, so you can see when an order was claimed: `GET /claims?order_id=shop-1001`.
- Requests without `order_id` behave as before (every request adds items).

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
| `503` | Service Unavailable: the database is not connected. |

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

# Same, safe to retry: a second call with the same order_id adds nothing
curl -i -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"item_name":"&b5 Diamonds","item_display":"DIAMOND","description":"Thanks for your purchase!","player":"Steve","command":"give <player> diamond 5","used":1,"order_id":"shop-1001"}' \
  http://127.0.0.1:1558/add-item

# List Steve's items
curl -H "X-API-Key: $KEY" http://127.0.0.1:1558/items/Steve

# Was order shop-1001 claimed?
curl -H "X-API-Key: $KEY" "http://127.0.0.1:1558/claims?order_id=shop-1001"

# Last 10 claims of the last 24 hours
curl -H "X-API-Key: $KEY" "http://127.0.0.1:1558/claims?limit=10&since=$(( ($(date +%s) - 86400) * 1000 ))"

# Delete item 12
curl -X DELETE -H "X-API-Key: $KEY" http://127.0.0.1:1558/item/12
```
