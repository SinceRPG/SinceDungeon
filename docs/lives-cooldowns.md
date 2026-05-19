---
layout: page
title: Lives and Cooldowns
---
SinceDungeon includes two progression gates:

- **Lives** control entry, death penalties, revives, and regeneration.
- **Cooldowns** control how soon players can rerun a dungeon.

## Lives Configuration

```yaml
lives:
  default-max-lives: 3
  default-start-lives: 3
  regen-interval-seconds: 3600
  regen-amount: 1
```

Dungeon files can require and deduct lives:

```yaml
settings:
  required-lives-to-join: 1
  lives-deducted-per-death: 1
```

## Out of Lives

Global config:

```yaml
dungeon:
  out-of-lives-action: "SPECTATE"
```

Common behaviors:

- `SPECTATE`: player becomes spectator.
- `KICK`: player is removed from the dungeon.
- `FAIL`: the team fails the dungeon.

## Soul Crystal

Soul Crystals can restore lives and are also used by `/dungeon revive`.

Admin command:

```text
/sincedungeonpremium givelifeitem <target> <amount>
```

Configured item:

```yaml
items:
  life_crystal:
    material: "NETHER_STAR"
    name: "&d&lSoul Crystal &8| &a+<amount> Lives"
```

## Reviving Teammates

```text
/dungeon revive <target>
```

Requirements:

- The sender must be in the same dungeon.
- The target must be a dungeon participant.
- The target must be in spectator mode.
- The sender must have a Soul Crystal in inventory.

## Lives Admin Commands

```text
/sincedungeonpremium lives <target> add <amount>
/sincedungeonpremium lives <target> set <amount>
/sincedungeonpremium lives <target> addmax <amount>
/sincedungeonpremium lives <target> setregenamount <amount>
/sincedungeonpremium lives <target> setregeninterval <seconds>
/sincedungeonpremium lives <target> resetregen
/sincedungeonpremium lives <target> check
```

## Cooldowns

Dungeon file:

```yaml
settings:
  cooldown-seconds: 1800
  cooldown-on-leave: true
```

Global fallback:

```yaml
dungeon:
  gameplay:
    cooldown-on-leave: false
```

`cooldown-on-leave` applies cooldowns even when a player leaves, disconnects, or fails.

## Cooldown Items

Reset ticket:

```text
/sincedungeonpremium givecooldownitem reset <target> <amount>
```

Reduction ticket:

```text
/sincedungeonpremium givecooldownitem reduce <target> <amount> <seconds>
```

Configured items:

```yaml
items:
  cooldown_reset: {}
  cooldown_reduce: {}
```

## Cooldown Admin Commands

```text
/sincedungeonpremium cooldown check <target> <map>
/sincedungeonpremium cooldown reset <target> <map>
/sincedungeonpremium cooldown resetall <target>
/sincedungeonpremium cooldown reduce <target> <seconds>
```

## PlaceholderAPI

```text
%sincedungeon_lives%
%sincedungeon_max_lives%
%sincedungeon_lives_regen_amount%
%sincedungeon_lives_regen_interval%
%sincedungeon_lives_time_to_regen%
%sincedungeon_cooldown_<map>%
```
