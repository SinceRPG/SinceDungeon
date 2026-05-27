---
layout: page
title: Folia and Schematic Instancing
---

Core's default `WORLD` instancing clones and loads full world folders at runtime. That is suitable for Paper, but Folia
does not support Bukkit runtime world creation. For Folia, use Premium schematic shared-world mode.

## Recommended Folia Setup

Requirements:

- SinceDungeon Core
- SinceDungeon Premium Addon
- WorldEdit or FastAsyncWorldEdit
- A shared schematic world preloaded by the server before SinceDungeon Premium enables

Premium config:

```yaml
instancing:
  mode: "SCHEMATIC"
  paste-y-level: 64
  schematic:
    paste-air: true
    shared-world:
      enabled: true
      name: "SDPremium_Schematic"
      spawn-location: "0,65,0"
      coordinate-y-offset: 0
      region-spacing: 2048
      region-radius: 512
      grid-width: 128
      clear-on-release: true
      clear-min-y: -64
      clear-max-y: 320
```

On Folia, the shared world must already be loaded. The plugin will not create or load it dynamically.

## Schematic Files

Put schematics in:

```text
plugins/SinceDungeon-PremiumAddon/schematics/
```

The schematic filename must match the dungeon `template-world` value:

```yaml
template-world: "ForgottenCrypt_Template"
```

Valid files:

```text
ForgottenCrypt_Template.schem
ForgottenCrypt_Template.schematic
```

## Coordinate Offsets

Dungeon YAML coordinates remain local to the schematic. The shared-world provider allocates an isolated region and Core
offsets action locations into that region at runtime.

This applies to spawn locations, stage actions, chests, levers, doors, walls, zones, traps, NPCs, and checkpoints.

## Cleanup

When a run ends, shared-world mode releases only the allocated region:

- Non-player entities in the region are removed.
- The region can be cleared with WorldEdit.
- The slot is returned to the reusable pool.

Full-world providers still unload and delete the generated dungeon world folder.

## Paper Setup

Paper can use either `WORLD` full world folder clone mode or `SCHEMATIC` mode. For large servers, schematic shared-world
mode avoids repeated world creation and reduces filesystem churn.
