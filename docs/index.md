---
layout: home
title: SinceDungeon Wiki
---

SinceDungeon is a Paper 1.21 dungeon plugin for instanced, configurable dungeon runs. It provides template-world
instancing, staged objectives, built-in party support, lives and cooldown systems, reward chests, leaderboards,
PlaceholderAPI support, an in-game editor, and an extensible Java API.

The project is split into two plugins:

- **SinceDungeon Core**: dungeon engine, commands, editor, actions, rewards, top boards, lives, cooldowns, database,
  Redis/cross-server support, and developer API.
- **SinceDungeon Premium Addon**: advanced dungeon actions, roulette rewards, hologram drops, hologram leaderboards,
  Mythic+ affixes, Discord webhooks, DecentHolograms support, and extra MythicMobs item integration.

## Quick Links

- [Getting Started](getting-started.md)
- [Configuration](configuration.md)
- [Dungeon Files](dungeon-files.md)
- [Actions](actions.md)
- [Rewards](rewards.md)
- [Commands](commands.md)
- [Leaderboards](leaderboards.md)
- [Lives and Cooldowns](lives-cooldowns.md)
- [Editor](editor.md)
- [Languages and Customization](languages-customization.md)
- [Permissions](permissions.md)
- [Integrations](integrations.md)
- [Premium Addon](premium.md)
- [Folia and Schematic Instancing](folia-schematic.md)
- [Developer API](developer-api.md)
- [Troubleshooting](troubleshooting.md)

## Requirements

- Paper API: **1.21**
- Java: **21**
- Optional integrations:
    - PlaceholderAPI
    - MythicMobs
    - MythicLib
    - MMOItems
    - DecentHolograms, Premium only
    - WorldEdit or FastAsyncWorldEdit, Premium schematic instancing
    - Redis and proxy messaging for experimental cross-server matchmaking

## Core Concepts

| Concept        | What it does                                                                                 |
|----------------|----------------------------------------------------------------------------------------------|
| Template world | A source world folder copied into a temporary dungeon instance.                              |
| Dungeon file   | A YAML file in `plugins/SinceDungeon/dungeons/<id>.yml`.                                     |
| Stage          | A numbered step in the dungeon. Each stage can contain one or more actions.                  |
| Action         | An objective such as reaching a location, clearing mobs, opening a door, or fighting a boss. |
| Rewards        | Completion grants reward chest sessions based on clear time tiers.                           |
| Party          | Built-in party system, with an API override for custom party plugins.                        |
| Lives          | Player life pool used for entry requirements, death penalties, and revive items.             |
| Cooldowns      | Per-dungeon cooldowns after completion or early exit, depending on config.                   |
| Top boards     | Database-backed leaderboard categories for time, party time, kills, and clears.              |

## Suggested Wiki Order

1. Install the Core plugin and optional dependencies.
2. Configure global settings in `config.yml`.
3. Create one template world folder.
4. Add a dungeon YAML file under `dungeons/`.
5. Test with `/dungeon join <id>`.
6. Use `/dungeon editor` for faster in-game editing.
7. Add rewards, conditions, lives, cooldowns, and leaderboards.
8. Install Premium if you need advanced actions, roulette rewards, affixes, holograms, or webhooks.
