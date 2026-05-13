# Core Actions

Actions are registered by the Core plugin and configured per stage.

Common action fields:

```yaml
type: "ACTION_TYPE"
time_limit: -1
time_penalty: 1
start_message:
  - "<yellow>Message"
notifications:
  custom_start: true
  init: true
  progress: true
  complete: true
  warning: true
```

## SPAWN_WAVE

Spawns vanilla mobs.

```yaml
guards:
  type: "SPAWN_WAVE"
  mob: "ZOMBIE"
  amount: 5
  scale_with_party: true
  custom_name: "<red>Guard"
  is_baby: false
  locations:
    - "0,64,0"
  attributes:
    - "movement_speed:0.3"
  equipment:
    - "HAND:IRON_SWORD"
  custom_drops:
    - "DIAMOND:1:25"
```

## REACH_LOCATION

Completes when players reach a location.

```yaml
arrival:
  type: "REACH_LOCATION"
  target: "10,64,10"
  radius: 3.0
```

## TELEPORT

Teleports participants to a location.

```yaml
teleport:
  type: "TELEPORT"
  location: "0,70,0"
  sound: "entity.enderman.teleport"
```

## LOOT_CHEST

Creates a loot chest objective.

```yaml
chest:
  type: "LOOT_CHEST"
  location: "20,60,-5"
  per_player: false
  required_key: "NONE"
  items:
    13: "KEY:gate_key:1"
    14: "GOLD_INGOT:5"
```

## BREAK_WALL

Breaks a cuboid wall after players trigger it.

```yaml
break_wall:
  type: "BREAK_WALL"
  trigger: "0,64,0"
  corner1: "-2,64,0"
  corner2: "2,68,0"
```

## MYTHIC_WAVE

Spawns MythicMobs mobs.

```yaml
mythic:
  type: "MYTHIC_WAVE"
  mob: "SkeletonKing"
  amount: 1
  level: 5
  scale_with_party: false
  target_to_kill: "NONE"
  locations:
    - "0,64,0"
```

## RANDOM_WAVE

Spawns a weighted random pool of vanilla or Mythic mobs.

```yaml
random:
  type: "RANDOM_WAVE"
  amount: 5
  scale_with_party: true
  locations:
    - "0,64,0"
  random_mobs:
    - "VANILLA:ZOMBIE:50"
    - "VANILLA:SKELETON:30"
    - "MYTHIC:SkeletonKing:20:1"
```

Format:

```text
VANILLA:<EntityType>:<weight>
MYTHIC:<MobId>:<weight>:<level>
```

## CONTROL_ZONE

Requires players to hold an area.

```yaml
zone:
  type: "CONTROL_ZONE"
  center: "0,64,0"
  start_radius: 10.0
  end_radius: 3.0
  required_time: 20
  mob: "NONE"
  mob_interval: 60
  mob_level: 1
```

## UNLOCK_DOOR

Requires a key and opens/removes a cuboid door.

```yaml
door:
  type: "UNLOCK_DOOR"
  key_id: "door_1"
  trigger: "0,64,-10"
  corner1: "-2,64,-10"
  corner2: "2,68,-10"
  particle: "ENCHANT"
```

## BOSS_BATTLE

Spawns a vanilla boss with scaling, boss bar, phases, enrage, equipment, and custom drops.

```yaml
boss:
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
  equipment: [ ]
  enrage_time: -1
  enrage_message: "&c&lThe Boss has become ENRAGED!"
  enrage_attributes:
    - "attack_damage:20.0"
  phases:
    50:
      message: "&cThe boss changes phase!"
      attributes:
        - "movement_speed:0.5"
      reinforcements:
        mob: "ZOMBIE"
        amount: 3
        custom_name: "&cMinion"
        attributes: [ ]
        equipment: [ ]
```
