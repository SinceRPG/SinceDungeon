---
layout: page
title: Integrations
---

## PlaceholderAPI

PlaceholderAPI is optional. If installed, SinceDungeon registers internal expansions and also uses PlaceholderAPI for
conditions and command rewards.

### Lives and Cooldown Placeholders

Identifier:

```text
sincedungeon
```

Placeholders:

| Placeholder                           | Output                                           |
|---------------------------------------|--------------------------------------------------|
| `%sincedungeon_lives%`                | Current lives.                                   |
| `%sincedungeon_max_lives%`            | Max lives.                                       |
| `%sincedungeon_lives_regen_amount%`   | Current regen amount.                            |
| `%sincedungeon_lives_regen_interval%` | Current regen interval in seconds.               |
| `%sincedungeon_lives_time_to_regen%`  | Time until next life.                            |
| `%sincedungeon_cooldown_<map>%`       | Remaining cooldown for a dungeon, or ready text. |

### Top Placeholders

Identifier:

```text
sincedungeontop
```

Format:

```text
%sincedungeontop_<category>_<map_id>_<rank>_<type>%
```

Categories:

- `fastest`
- `partyfastest`
- `kills`
- `clears`

Types:

- `name`
- `value`

Examples:

```text
%sincedungeontop_fastest_example_dungeon_1_name%
%sincedungeontop_fastest_example_dungeon_1_value%
%sincedungeontop_kills_example_dungeon_3_name%
```

Top placeholders are cached asynchronously every five minutes.

## MythicMobs

Core uses MythicMobs for:

- `MYTHIC_WAVE`
- Random wave Mythic entries
- Mythic mob detection
- Mythic mob spawning by internal name and level

Premium adds:

- `MYTHIC_ITEM` item provider
- `MYTHIC_ITEM` reward processor

## MMOItems

Core supports MMOItems rewards and item parsing:

```text
MMOITEMS:<type>:<id>:<amount>
```

Reward shortcut:

```yaml
type: "MMOITEM"
value: "SWORD:SILVER_LANCE:1"
```

## Native TextDisplay Holograms

Premium creates leaderboard holograms with Minecraft native `TextDisplay` entities. DecentHolograms is not required.

```text
/sdp hologram create <map_id> <category>
```

## Redis and Proxy Messaging

Cross-server dungeon routing uses:

- Redis Pub/Sub for server communication.
- BungeeCord or Velocity plugin messaging channel for player transfers.

The feature is experimental in the default config and should be tested carefully before production use.

## Discord Webhooks, Premium

Premium can send completion notifications to Discord:

```yaml
webhooks:
  enabled: true
  url: "YOUR_DISCORD_WEBHOOK_URL_HERE"
  embed-title: "Dungeon Cleared!"
  embed-color: "5814783"
  embed-description: "Map: **%dungeon%**\nTime: **%time%s**\nPlayers: **%players%**"
```
