---
layout: page
title: Dungeon Files
---

Each dungeon is a YAML file:

```text
plugins/SinceDungeon/dungeons/<dungeon_id>.yml
```

The filename without `.yml` is the dungeon ID used by commands, leaderboards, API calls, and Premium affix
configuration.

## Top-Level Structure

```yaml
template-world: "ForgottenCrypt_Template"
public: true

settings: { }
conditions: { }
rewards: { }
stages: { }
```

## Template World

```yaml
template-world: "ForgottenCrypt_Template"
```

The value must match the source world folder name. SinceDungeon creates temporary instance worlds from this template.

## Public Visibility

```yaml
public: true
```

Public dungeons appear in tab completion for player-facing commands such as `/dungeon join` and `/dungeon top`.

When `public: false`, normal members cannot join the dungeon, even if they manually type the dungeon ID. Admins with
`SinceDungeon.admin` can still see private dungeons in tab completion and can join them for testing.

## Settings

```yaml
settings:
  max-players: 6
  required-lives-to-join: 1
  lives-deducted-per-death: 1
  keep-inventory-on-death: true
  prevent-item-dropping: true
  block-ender-pearls: true
  kick-delay-after-finish: 15
  force-daylight-and-clear-weather: true
  save-and-restore-stats: true
  death-action: "RESPAWN"
  clear-mob-drops: true
  randomize-stages: false
  cooldown-seconds: 1800
  cooldown-on-leave: true
  required-item: "NONE"
  consume-required-item: true
```

Death actions:

- `RESPAWN`: respawn at the dungeon start or checkpoint.
- `FAIL`: fail the entire run.
- `SPECTATE`: let the player watch remaining teammates.

Command hooks:

```yaml
settings:
  commands:
    on-start:
      - "lp user %player% permission set dungeon.in_game true"
    on-finish:
      - "lp user %player% permission unset dungeon.in_game"
    on-first-finish:
      - "eco give %player% 5000"
```

## Conditions

Conditions are checked before the player enters.

```yaml
conditions:
  level_check:
    name: "Level Requirement"
    check: "%player_level%;>=;10"
    msg: "<red>You must be at least Level 10 to enter this raid!"
```

Default condition processor:

- `PAPI`: PlaceholderAPI expression checks.

Condition format:

```text
<left>;<operator>;<right>
```

Supported numeric operators:

- `>=`
- `<=`
- `>`
- `<`
- `==`
- `!=`

Supported string operators:

- `==`
- `!=`
- `equalsIgnoreCase`

## Stages

Stages are numeric and run in order unless `randomize-stages` or Premium branching/jump actions alter flow.

```yaml
stages:
  1:
    chance: 100.0
    commands:
      - "say Stage 1 completed by %player%"
    actions:
      arrival:
        type: "REACH_LOCATION"
        target: "10,64,10"
        radius: 3.0
```

| Key        | Meaning                                  |
|------------|------------------------------------------|
| `chance`   | Chance for the stage to appear.          |
| `commands` | Console commands after stage completion. |
| `actions`  | Named action blocks.                     |

## Coordinates

Most locations use:

```text
x,y,z
```

Example:

```yaml
target: "10,64,-5"
```

Lists of locations:

```yaml
locations:
  - "15,64,20"
  - "5,64,20"
```

## Stage Insertion

Admins can insert a stage into an existing dungeon file and shift later stage numbers:

```text
/sincedungeon stage insert <map_id> <position>
/sdp stage insert <map_id> <position>
```
