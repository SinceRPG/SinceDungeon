---
layout: page
title: Getting Started
---

# Getting Started

## Installation

1. Build or download `SinceDungeon.jar`.
2. Place it in the server `plugins/` folder.
3. Start the server once to generate plugin files.
4. Stop the server before editing large YAML files.
5. Put template worlds in the configured template folder.
6. Create dungeon files inside `plugins/SinceDungeon/dungeons/`.
7. Start the server and run `/dungeon join <dungeon_id>`.

For Premium features, also place `SinceDungeon-PremiumAddon.jar` in `plugins/`. Premium requires Core to be installed and enabled.

On Folia, use Premium schematic shared-world mode. Core's default full-world cloning relies on runtime world creation, which Folia does not support.

## Build From Source

The project uses Gradle and Java 21.

```bash
./gradlew build
```

Artifacts are generated in:

```text
build/libs/
```

Expected artifact names:

- `SinceDungeon-<version>.jar`
- `SinceDungeon-PremiumAddon-<version>.jar`

## First Dungeon Checklist

- Create or copy a world folder for the dungeon template.
- Set `template-world` in the dungeon file to that folder name.
- Keep `public: true` if regular players should see and join it. Use `public: false` for private test dungeons; admins can still join them.
- Add at least one stage with at least one action.
- Add reward tiers and reward pool entries if completion should grant loot.
- Test with an operator account first.

Minimal dungeon:

```yaml
template-world: "ForgottenCrypt_Template"
public: true

settings:
  max-players: 4
  required-lives-to-join: 1
  lives-deducted-per-death: 1
  cooldown-seconds: 1800

rewards:
  solo-tiers:
    600: 1
  party-tiers:
    500: 1
  pool:
    diamond:
      type: "ITEM"
      value: "DIAMOND:1-3"
      chance: 100.0
      name: "<aqua>Diamonds"

stages:
  1:
    chance: 100.0
    actions:
      start:
        type: "REACH_LOCATION"
        target: "0,64,0"
        radius: 3.0
```

## File Locations

| File or folder | Purpose |
| --- | --- |
| `plugins/SinceDungeon/config.yml` | Main merged Core configuration. |
| `plugins/SinceDungeon/dungeons/` | Dungeon template YAML files. |
| `plugins/SinceDungeon/languages/` | Language files. |
| `plugins/SinceDungeon-PremiumAddon/config.yml` | Premium configuration. |
| Template world folder | Source world copied for each instance. |

## Reloading

Use:

```text
/sincedungeonpremium reload
```

or the configured admin command and aliases. Premium also provides:

```text
/sdp reload
```

Server restart is still recommended after changing dependencies, template world folders, database backend, or cross-server settings.
