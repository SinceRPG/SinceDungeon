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
  lives-deducted-on-leave: 0
  lives-deducted-on-fail: 0
  lives-deducted-on-clear: 0
```

`lives-deducted-per-death` is charged for each death during the run. The outcome settings are charged when that
outcome happens: leave/disconnect before clear, dungeon failure, or dungeon clear. They stack with any per-death losses
already taken, and `0` disables that specific outcome cost.

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
/sincedungeon givelifeitem <target> <amount> [-s]
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
/sincedungeon lives <target> add <amount> [-s]
/sincedungeon lives <target> reduce <amount> [-s]
/sincedungeon lives <target> set <amount> [-s]
/sincedungeon lives <target> addmax <amount> [-s]
/sincedungeon lives <target> setregenamount <amount> [-s]
/sincedungeon lives <target> setregeninterval <seconds> [-s]
/sincedungeon lives <target> resetregen [-s]
/sincedungeon lives <target> check [-s]
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

`cooldown-on-leave` applies cooldowns even when a player leaves, disconnects, or fails. It does not enable or disable
`lives-deducted-on-leave` or `lives-deducted-on-fail`; those settings are controlled independently.

## Cooldown Items

Reset ticket:

```text
/sincedungeon givecooldownitem reset <target> <amount>
```

Reduction ticket:

```text
/sincedungeon givecooldownitem reduce <target> <amount> <seconds>
```

Configured items:

```yaml
items:
  cooldown_reset: { }
  cooldown_reduce: { }
```

## Cooldown Admin Commands

```text
/sincedungeon cooldown check <target> <map>
/sincedungeon cooldown reset <target> <map>
/sincedungeon cooldown resetall <target>
/sincedungeon cooldown reduce <target> <seconds>
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
