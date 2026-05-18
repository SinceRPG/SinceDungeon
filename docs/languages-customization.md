---
layout: page
title: Languages and Customization
---

# Languages and Customization

SinceDungeon ships with language folders for:

- `en`
- `vi`
- `zh`

Set the active locale:

```yaml
settings:
  locale: "en"
```

## Language Files

Language files are grouped by feature:

```text
languages/en/admin.yml
languages/en/cooldown.yml
languages/en/cross_server.yml
languages/en/editor.yml
languages/en/error.yml
languages/en/game.yml
languages/en/general.yml
languages/en/lives.yml
languages/en/party.yml
languages/en/reward.yml
languages/en/top.yml
```

Premium injects additional language keys when it loads or reloads, so Premium messages can coexist with Core language files.

## Text Formatting

The plugin uses MiniMessage-style formatting in many examples:

```text
<red>Error text
<green>Success text
<b#fbff00>Hex colored bold text
```

Legacy ampersand color codes are also used in default configs:

```text
&aGreen text
&cRed text
&6Gold text
```

## Custom Item Display

Items can be customized through config:

```yaml
items:
  life_crystal:
    material: "NETHER_STAR"
    name: "&d&lSoul Crystal"
    lore:
      - "&7A mythical crystal."
    glowing: true
    custom-model-data: 1001
```

Supported item-related options include:

- `material`
- `name`
- `lore`
- `glowing`
- `unbreakable`
- `max-stack-size`
- `flags`
- `custom-model-data`
- `rarity`

## Sounds and Particles

Core feedback settings:

```yaml
sounds:
  door_locked: "block.chest.locked"
  door_unlock: "block.iron_door.open"
  game_start: "entity.ender_dragon.growl"

particles:
  zone_border: "FLAME"
  reach_location_idle: "HAPPY_VILLAGER"
```

Premium adds its own sound and particle settings:

```yaml
sounds:
  puzzle_success: "block.note_block.chime"
  puzzle_fail: "block.note_block.bass"
  roulette_tick: "block.note_block.hat"
  roulette_finish: "entity.player.levelup"

particles:
  affix_vampiric: "HEART"
  affix_volcanic_warn: "FLAME"
  affix_volcanic_boom: "EXPLOSION"
```

## Notification Toggles

Action notifications can be enabled or disabled per action:

```yaml
action-notifications:
  spawn_wave:
    custom_start: true
    init: true
    progress: true
    complete: true
```

Useful keys include:

- `custom_start`
- `init`
- `progress`
- `warning`
- `complete`
