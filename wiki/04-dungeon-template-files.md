# Dungeon Template Files

Dungeon templates are YAML files stored in:

```text
plugins/SinceDungeon/dungeons/
```

The file name is the dungeon ID.

Example:

```text
plugins/SinceDungeon/dungeons/forgotten_crypt.yml
```

Dungeon ID:

```text
forgotten_crypt
```

## Basic Structure

```yaml
template-world: "ForgottenCrypt_Template"
public: true

settings:
  max-players: 6

conditions: { }

rewards: { }

stages: { }
```

## `template-world`

Core world mode:

- `template-world` is the world folder to clone.
- This mode is blocked on Folia because Folia cannot create Bukkit worlds at runtime.

Premium schematic mode:

- `template-world` is the schematic file base name.
- `ForgottenCrypt_Template` maps to `ForgottenCrypt_Template.schem`.
- On Folia, use Premium `SCHEMATIC` shared-world mode with the shared world preloaded.

## `public`

Controls whether the dungeon appears in public listings and leaderboard menus.

```yaml
public: true
```

## Settings

```yaml
settings:
  max-players: 6
  required-lives-to-join: 1
  lives-deducted-per-death: 1
  lives-deducted-on-leave: 0
  lives-deducted-on-fail: 0
  lives-deducted-on-clear: 0
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
  commands:
    on-start: [ ]
    on-finish: [ ]
    on-first-finish: [ ]
```

Death actions:

- `RESPAWN`: player respawns inside the dungeon.
- `FAIL`: death ends or fails the run.
- `SPECTATE`: dead player spectates remaining players.

Life costs:

- `lives-deducted-per-death` is charged each time a player dies.
- `lives-deducted-on-leave` is charged when a player leaves or disconnects before clear.
- `lives-deducted-on-fail` is charged when the dungeon fails.
- `lives-deducted-on-clear` is charged when the dungeon clears.
- Outcome costs are independent from per-death costs; set an outcome cost to `0` to disable it.
- `cooldown-on-leave` only controls cooldowns on leave/fail and does not change life costs.

## Conditions

Conditions use registered condition processors. The default processor is PlaceholderAPI.

```yaml
conditions:
  level_check:
    name: "Level Requirement"
    check: "%player_level%;>=;10"
    msg: "<red>You must be at least Level 10!"
```

## Rewards

Rewards define how many reward chests players receive based on completion time.

```yaml
rewards:
  solo-tiers:
    300: 3
    600: 2
    1200: 1
  party-tiers:
    240: 3
    500: 2
    1000: 1
  pool:
    reward_1:
      type: "ITEM"
      value: "DIAMOND:3-8"
      chance: 50.0
      name: "<yellow>Diamonds"
```

## Stages

Stages are executed in numeric order unless randomization is enabled.

```yaml
stages:
  1:
    chance: 100.0
    commands:
      - "say Stage started"
    actions:
      arrival:
        type: "REACH_LOCATION"
        target: "10,64,10"
        radius: 3.0
```

Each action key only needs to be unique inside its stage.
