---
layout: page
title: Rewards
---

Dungeon completion grants reward chests based on completion time. The default Core system opens a reward GUI; Premium
can replace this with roulette-style spins.

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

The key is completion time in seconds. The value is the number of reward chests or spins.

`rewards.tiers` is supported as a backward-compatible fallback for solo tiers.

## Reward Pool

```yaml
rewards:
  pool:
    reward_1:
      type: "ITEM"
      value: "DIAMOND:3-8"
      chance: 50.0
      name: "<b#fbff00>Shiny Diamonds"
      lore:
        - "<gray>A bright reward."
```

| Key      | Meaning                                     |
|----------|---------------------------------------------|
| `type`   | Reward processor type.                      |
| `value`  | Raw reward value.                           |
| `chance` | Weight/chance used during reward selection. |
| `name`   | Display name in reward UI/messages.         |
| `lore`   | Optional reward lore.                       |

## Built-In Reward Types

### COMMAND

Runs a console command. `%player%` is replaced with the receiving player name, and PlaceholderAPI placeholders are
parsed if PlaceholderAPI is installed.

```yaml
type: "COMMAND"
value: "eco give %player% 1000"
chance: 100.0
name: "<green>$1,000 Cash"
```

### ITEM

Gives a vanilla item.

```yaml
type: "ITEM"
value: "DIAMOND:3-8"
```

### MMOITEM

Gives an MMOItems item.

```yaml
type: "MMOITEM"
value: "SWORD:SILVER_LANCE:1"
```

### LIFE_ITEM

Gives a configured Soul Crystal.

```yaml
type: "LIFE_ITEM"
value: "1"
```

### COOLDOWN_RESET

Gives a cooldown reset ticket.

```yaml
type: "COOLDOWN_RESET"
value: "1"
```

### COOLDOWN_REDUCE

Gives a cooldown reduction ticket.

```yaml
type: "COOLDOWN_REDUCE"
value: "300:1"
```

Format:

```text
<seconds>:<amount>
```

### MYTHIC_ITEM, Premium

Premium registers MythicMobs items as rewards.

```yaml
type: "MYTHIC_ITEM"
value: "MythicItemInternalName:1"
```

## Item Data Formats

Core item providers understand:

```text
MATERIAL:AMOUNT
MATERIAL:MIN-MAX
KEY:<key_id>:<amount>
LIFE_ITEM:<amount>
COOLDOWN_RESET:<amount>
COOLDOWN_REDUCE:<seconds>:<amount>
MMOITEMS:<type>:<id>:<amount>
```

Premium adds:

```text
MYTHIC_ITEM:<internal_name>:<amount>
```

## Safe Item Delivery

The API gives items safely:

- Adds items to inventory when possible.
- Drops overflow if the inventory is full.
- Avoids dropping items inside temporary dungeon worlds when a safer saved location exists.
