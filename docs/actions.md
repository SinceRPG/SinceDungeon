---
layout: page
title: Actions
---

Actions are stage objectives. Each action block must include a `type`.

```yaml
stages:
  1:
    actions:
      objective_name:
        type: "REACH_LOCATION"
        target: "10,64,10"
        radius: 3.0
```

Common optional fields loaded by the action system include:

- `time_limit`: seconds before the action is penalized or failed, where supported.
- `time_penalty`: lives or time penalty value used by supported actions.
- `start_message`: one string or list of strings shown when the action starts.

## Core Actions

### REACH_LOCATION

Players must move near a coordinate.

```yaml
type: "REACH_LOCATION"
target: "10,64,10"
radius: 3.0
```

### TELEPORT

Teleports all participants.

```yaml
type: "TELEPORT"
location: "0,64,0"
sound: "entity.enderman.teleport"
```

### SPAWN_WAVE

Spawns vanilla mobs and completes when they are killed.

```yaml
type: "SPAWN_WAVE"
mob: "ZOMBIE"
amount: 5
scale_with_party: true
custom_name: "<red>Crypt Guard"
is_baby: false
attributes:
  - "generic.max_health:40"
equipment:
  - "HAND:IRON_SWORD"
custom_drops:
  - "DIAMOND:1"
locations:
  - "15,64,20"
```

### MYTHIC_WAVE

Spawns MythicMobs mobs or bosses.

```yaml
type: "MYTHIC_WAVE"
mob: "LichKing"
amount: 1
level: 5
scale_with_party: false
target_to_kill: "NONE"
locations:
  - "0,64,-50"
seal:
  enabled: true
  material: "BARRIER"
  regions:
    - corner1: "-2,64,-35"
      corner2: "2,67,-35"
```

Requires MythicMobs for actual Mythic mob spawning.

### RANDOM_WAVE

Spawns a weighted mix of vanilla and Mythic mobs.

```yaml
type: "RANDOM_WAVE"
amount: 8
scale_with_party: true
random_mobs:
  - "VANILLA:ZOMBIE:50"
  - "VANILLA:SKELETON:30"
  - "MYTHIC:SkeletonKing:20:1"
locations:
  - "0,64,0"
```

Format:

```text
VANILLA:<entity_type>:<weight>
MYTHIC:<mob_id>:<weight>:<level>
```

### LOOT_CHEST

Creates a chest objective.

```yaml
type: "LOOT_CHEST"
location: "20,60,-5"
per_player: false
required_key: "NONE"
items:
  13: "KEY:gate_key:1"
  14: "GOLD_INGOT:5"
```

`per_player: true` lets each player collect their own share.

### UNLOCK_DOOR

Requires a key item and removes or opens a block region.

```yaml
type: "UNLOCK_DOOR"
key_id: "gate_key"
trigger: "0,64,-10"
corner1: "-2,64,-10"
corner2: "2,68,-10"
particle: "SOUL_FIRE_FLAME"
```

### BREAK_WALL

Players break a trigger block to remove a configured wall region.

```yaml
type: "BREAK_WALL"
trigger: "0,64,0"
corner1: "-2,64,0"
corner2: "2,68,0"
```

The implementation guards against excessive wall volume.

### CONTROL_ZONE

Players hold an area for a duration. The zone can shrink over time and optionally spawn attackers.

```yaml
type: "CONTROL_ZONE"
center: "0,64,0"
start_radius: 10.0
end_radius: 3.0
required_time: 20
mob: "ZOMBIE"
mob_interval: 60
mob_level: 1
custom_name: "<red>Zone Invader"
```

### BOSS_BATTLE

Spawns a vanilla boss with health scaling, boss bar, phases, enrage, drops, and optional sealing.

```yaml
type: "BOSS_BATTLE"
location: "0,64,0"
mob: "ZOMBIE"
custom_name: "&4&lThe Boss"
base_health: 500.0
scale_health_per_player: 150.0
bar_color: "RED"
bar_style: "SOLID"
attributes:
  - "movement_speed:0.3"
equipment: []
enrage_time: 120
enrage_message: "&c&lThe Boss has become ENRAGED!"
enrage_attributes:
  - "attack_damage:20.0"
custom_drops:
  - "DIAMOND:1-3"
seal:
  enabled: true
  material: "BARRIER"
  regions:
    - corner1: "-3,64,0"
      corner2: "3,67,0"
phases:
  75:
    message: "&eThe boss changes tactics!"
    attributes:
      - "movement_speed:0.4"
    reinforcements:
      mob: "ZOMBIE"
      amount: 3
      custom_name: "&cMinion"
```

## Premium Actions

Premium registers these additional action types:

| Type                 | Purpose                                       |
|----------------------|-----------------------------------------------|
| `BUFF`               | Applies a potion effect to all participants.  |
| `ESCORT_NPC`         | Protect an NPC while it moves to a target.    |
| `BRANCHING_PATH`     | Let players choose between two future stages. |
| `LEVER_PUZZLE`       | Requires levers to be activated in sequence.  |
| `CHECKPOINT`         | Updates respawn/checkpoint location.          |
| `DAMAGE_ZONE`        | Creates a damaging area hazard.               |
| `JUMP_STAGE`         | Forces the run to continue at another stage.  |
| `CINEMATIC_DIALOGUE` | Plays timed title/chat/sound dialogue frames. |
| `PROJECTILE_TRAP`    | Fires projectiles repeatedly.                 |
| `DEFEND_CORE`        | Protect an entity for a duration.             |
| `GIVE_ITEM`          | Gives a configured item to participants.      |
| `PLAY_SOUND`         | Plays a sound for the party.                  |

See [Premium Addon](premium.md) for full examples.
