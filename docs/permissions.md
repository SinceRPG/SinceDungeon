---
layout: page
title: Permissions
---

## Admin Permission

```text
SinceDungeon.admin
```

Default: operator.

This permission controls:

- Core admin command tree.
- `/dungeon editor`
- `/dungeon spectate`
- `/dungeon getkey`
- Premium `/sdp` commands.
- Admin target usage where checked by command logic.

## Multiverse Inventory Bypass

Core config includes bypass permissions that can be temporarily granted to prevent inventory clearing issues when a
dungeon instance world is deleted while a player is inside it:

```yaml
settings:
  mvi-bypass-permissions:
    - "mvinv.bypass.*"
    - "Multiverse-Inventories.bypass.*"
```

## Command Blocking

Inside dungeons, command use can be restricted:

```yaml
dungeon:
  gameplay:
    block-commands: true
    allowed-commands:
      - "/party"
      - "/p"
      - "/dungeon"
      - "/sincedungeon"
      - "/sincedungeonpremium"
```

Teleport commands can be blocked separately:

```yaml
dungeon:
  gameplay:
    block-teleport-commands: false
```
