# Rewards and Custom Items

## Reward Flow

At dungeon completion:

1. The plugin calculates completion time.
2. It checks solo or party tier tables.
3. It gives reward chest attempts.
4. Each reward chest rolls entries from `rewards.pool`.

## Reward Tiers

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
```

Keys are time in seconds. Values are chest counts.

## Reward Pool

```yaml
rewards:
  pool:
    reward_1:
      type: "ITEM"
      value: "DIAMOND:3-8"
      chance: 50.0
      name: "<yellow>Diamonds"
      lore:
        - "<gray>A dungeon reward"
```

## Built-In Reward Types

### COMMAND

Runs a console command.

```yaml
type: "COMMAND"
value: "eco give %player% 1000"
```

### ITEM

Gives a vanilla item.

```yaml
type: "ITEM"
value: "DIAMOND:3-8"
```

### MMOITEM

Gives an MMOItems item if MMOItems is installed.

```yaml
type: "MMOITEM"
value: "SWORD:SILVER_LANCE:1"
```

### LIFE_ITEM

Gives a life item.

```yaml
type: "LIFE_ITEM"
value: "1"
```

### COOLDOWN_RESET

Gives a cooldown reset item.

```yaml
type: "COOLDOWN_RESET"
value: "1"
```

### COOLDOWN_REDUCE

Gives a cooldown reduce item.

```yaml
type: "COOLDOWN_REDUCE"
value: "300:1"
```

## Custom Item Providers

Internal dynamic item strings include:

```text
KEY:<key-id>:<amount>
LIFE_ITEM:<amount>
COOLDOWN_RESET:<amount>
COOLDOWN_REDUCE:<seconds>:<amount>
MMOITEMS:<type>:<id>:<amount>
MATERIAL:<amount>
MATERIAL:<min>-<max>
```

Examples:

```text
KEY:gate_key:1
DIAMOND:3-8
COOLDOWN_REDUCE:300:1
MMOITEMS:SWORD:SILVER_LANCE:1
```

## Inventory Safety

If the player inventory is full, rewards are safely dropped at a valid location. If the player is inside a dungeon
instance, the plugin attempts to avoid dropping rewards into a deleted/generated dungeon world.
