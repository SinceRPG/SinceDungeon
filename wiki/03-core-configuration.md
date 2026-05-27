# Core Configuration

Core configuration is stored in:

```text
plugins/SinceDungeon/config.yml
```

## Locale

```yaml
settings:
  locale: "en"
```

Supported bundled locales:

- `en`
- `vi`
- `zh`

Language files are modular under:

```text
plugins/SinceDungeon/languages/<locale>/
```

## Database

SinceDungeon supports SQLite and MySQL.

```yaml
database:
  type: "sqlite"
  host: "localhost"
  port: 3306
  database: "sincedungeonpremium"
  username: "root"
  password: ""
  pool:
    max-size: 10
    min-idle: 2
    max-lifetime: 1800000
    timeout: 5000
```

Use SQLite for small servers. Use MySQL for large networks or multi-server setups.

Database stores:

- Fastest solo clear records
- Fastest party clear records
- Kill records
- Clear count records
- Player lives
- Player cooldowns

## Startup

```yaml
startup:
  async-timeout-seconds: 30
```

This controls how long the plugin waits for async startup data loading before failing startup. Increase it if the
database is remote or the server has many dungeon files.

## Cross-Server Mode

Cross-server mode is experimental and uses Redis Pub/Sub plus proxy transfer messaging.

```yaml
cross-server:
  enabled: false
  transfer-timeout-seconds: 30
  server-name: "dungeon-node-1"
  bungee-channel: "BungeeCord"
  return-server: "lobby"
```

Redis settings:

```yaml
redis:
  host: "localhost"
  port: 6379
  password: ""
  timeout-millis: 2000
  reconnect-delay-millis: 5000
  pool:
    max-total: 10
  channel: "SinceDungeon"
```

All dungeon servers in the network must share the same Redis channel.

## Command Names

Commands are configurable to avoid conflicts with other plugins.

```yaml
commands:
  party: "party"
  dungeon: "dungeon"
  admin: "sincedungeon"
  admin-aliases:
    - "sincedungeonpremium"
    - "sd"
    - "sdungeon"
```

## Generated Settings Files

Additional settings are split into:

```text
settings/actions.yml
settings/effects.yml
settings/gameplay.yml
settings/items.yml
settings/menus.yml
```

These files control GUI items, gameplay defaults, effects, menu layouts, and action defaults used by the editor.
