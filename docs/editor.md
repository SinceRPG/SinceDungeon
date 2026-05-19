---
layout: page
title: Editor
---
SinceDungeon includes an in-game editor for admins.

```text
/dungeon editor
```

Permission:

```text
SinceDungeon.admin
```

## What the Editor Is For

The editor is designed to reduce manual YAML work for common dungeon setup tasks:

- Create and adjust dungeon actions.
- Configure action parameters.
- Edit locations, radius values, sounds, particles, and action metadata.
- Use registered Core and Premium actions.
- Save changes back into dungeon configuration files.

## Editor Limits

Configured in `settings/menus.yml`:

```yaml
editor:
  nav-item: "ARROW"
  limits:
    max-locations: 50
    max-mob-amount: 200
    max-radius: 100.0
```

These limits protect admins from accidentally creating huge action lists, oversized radii, or excessive mob counts.

## Action Defaults

The editor uses defaults from:

```yaml
action-defaults:
```

Core defaults are in the Core settings files. Premium defaults are in Premium `config.yml`.

Example:

```yaml
action-defaults:
  spawn_wave:
    mob: "ZOMBIE"
    amount: 1
    scale_with_party: false
    time_limit: -1
    time_penalty: 1
```

## Premium Editor Extensions

When Premium is installed, it registers extra action types into the same action registry. They become available to the editor through the API registration layer.

Premium actions include:

- `BUFF`
- `ESCORT_NPC`
- `BRANCHING_PATH`
- `LEVER_PUZZLE`
- `CHECKPOINT`
- `DAMAGE_ZONE`
- `JUMP_STAGE`
- `CINEMATIC_DIALOGUE`
- `PROJECTILE_TRAP`
- `DEFEND_CORE`
- `GIVE_ITEM`
- `PLAY_SOUND`

## When to Edit YAML Manually

Manual YAML editing is still useful for:

- Complex boss phases.
- Large reward pools.
- Stage command hooks.
- Conditions.
- Premium affix assignment.
- Webhook, database, Redis, and cross-server settings.
