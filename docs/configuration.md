---
layout: page
title: Configuration
---

SinceDungeon loads Core configuration from `config.yml`, plus setting files under `settings/`. Dungeon-specific files
can override many gameplay settings per map.

## Cross-Server

```yaml
cross-server:
  enabled: false
  transfer-timeout-seconds: 30
  server-name: "dungeon-node-1"
  bungee-channel: "BungeeCord"
  return-server: "lobby"
```

Cross-server mode is marked experimental in the default config. It uses proxy plugin messaging and Redis Pub/Sub so a
lobby server can request a dungeon run on a dungeon node.

## Redis

```yaml
redis:
  host: "localhost"
  port: 6379
  password: ""
  channel: "SinceDungeon"
```

All servers in the same network must use the same Redis channel.

## Database

```yaml
database:
  type: "sqlite"
  host: "localhost"
  port: 3306
  database: "sincedungeonpremium"
  username: "root"
  password: ""
```

Supported backends:

- `sqlite`: local file storage, recommended for small servers.
- `mysql`: remote database, recommended for large or networked servers.

HikariCP pool settings are available under `database.pool`.

## Commands

Core command labels can be changed:

```yaml
commands:
  party: "party"
  party-aliases: [ "p", "pt" ]
  dungeon: "dungeon"
  dungeon-aliases: [ "dg", "inst" ]
  admin: "sincedungeon"
  admin-aliases: [ "sincedungeonpremium", "sd", "sdungeon" ]
```

Premium's command is fixed as `/sdp`, with alias `/sdpremium`.

## Locale and Debug

```yaml
settings:
  locale: "en"
  debug: false
  clear-remaining-mobs-on-action-complete: true
```

Available bundled locales include English, Vietnamese, and Chinese.

## Party System

```yaml
party:
  max-members: 4
  allow-friendly-fire: false
  max-join-distance: 50.0
  reward-share-mode: "EQUAL"
  system-name: "System"
  invite-timeout: 60
```

Reward share modes:

- `EQUAL`: all eligible party members receive rewards.
- `LEADER_ONLY`: only the party leader receives reward chests.

## Dungeon Gameplay Defaults

```yaml
dungeon:
  lobby-countdown: 10
  template-folder: "dungeons"
  world-prefix: "SinceDungeon_"
  death-action: "RESPAWN"
  top-awarded-to: "ALL_MEMBERS"
  clear-mob-drops: true
  save-and-restore-stats: false
  out-of-lives-action: "SPECTATE"
```

Important options:

| Option                | Meaning                                                 |
|-----------------------|---------------------------------------------------------|
| `lobby-countdown`     | Delay before an instance starts.                        |
| `template-folder`     | Folder containing template worlds.                      |
| `world-prefix`        | Prefix for generated dungeon instance worlds.           |
| `death-action`        | Default death behavior.                                 |
| `out-of-lives-action` | Behavior when a player has no lives left.               |
| `top-awarded-to`      | Whether all members or leader only receive top entries. |

Gameplay restrictions:

```yaml
dungeon:
  gameplay:
    keep-inventory-on-death: true
    prevent-item-dropping: true
    block-ender-pearls: true
    block-commands: true
    block-teleport-commands: false
    allowed-commands:
      - "/party"
      - "/p"
      - "/dungeon"
      - "/sincedungeon"
      - "/sincedungeonpremium"
```

## Lives

```yaml
lives:
  default-max-lives: 3
  default-start-lives: 3
  regen-interval-seconds: 3600
  regen-amount: 1
```

Players can spend lives to join dungeons and lose lives on death depending on dungeon settings.

## Items

Configurable built-in items:

- `items.key`
- `items.compass`
- `items.cooldown_reset`
- `items.cooldown_reduce`
- `items.life_crystal`

Item options include material, name, lore, glowing, rarity, flags, custom model data, max stack size, sounds, and
particles.

## Menus

Menu settings control the editor, reward GUI, and leaderboard GUI:

```yaml
editor:
  nav-item: "ARROW"
  limits:
    max-locations: 50
    max-mob-amount: 200
    max-radius: 100.0

reward:
  session-expire-seconds: 300

leaderboard:
  fetch-limit: 50
  gui-size: 54
  date-format: "dd/MM/yyyy HH:mm"
```
