# Installation

## Requirements

- Paper server compatible with the plugin build target.
- Java 21 or newer.
- PlaceholderAPI is optional but recommended for conditions and placeholders.
- MythicMobs is optional for Mythic actions.
- MMOItems is optional for MMOItems rewards/items.
- SinceDungeon Premium requires SinceDungeon Core.
- Premium schematic mode requires WorldEdit or FastAsyncWorldEdit.
- DecentHolograms is optional for Premium hologram leaderboards.

## Core Installation

1. Stop the server.
2. Put the Core jar in `plugins/`.
3. Start the server once to generate files.
4. Edit `plugins/SinceDungeon/config.yml`.
5. Restart or run the reload command.

Generated Core folders:

```text
plugins/SinceDungeon/
  config.yml
  data.db
  dungeons/
  languages/
  settings/
```

## Premium Installation

1. Install SinceDungeon Core first.
2. Put the Premium jar in `plugins/`.
3. Start the server once.
4. Edit `plugins/SinceDungeonPremium/config.yml`.
5. If using schematic mode, place files in:

```text
plugins/SinceDungeonPremium/schematics/
```

## Recommended First Setup

1. Install Core.
2. Create one dungeon template in `plugins/SinceDungeon/dungeons/`.
3. Run `/dungeon editor` and configure the dungeon from the GUI.
4. Set `public: true` if the dungeon should appear in public menus.
5. Add rewards and at least one stage/action.
6. Save in the editor.
7. Test with `/dungeon join <dungeon>`.

## Schematic Mode Setup

Premium schematic mode uses the dungeon `template-world` value as the schematic file name.

Example:

```yaml
template-world: "ForgottenCrypt_Template"
```

The Premium plugin will look for:

```text
plugins/SinceDungeonPremium/schematics/ForgottenCrypt_Template.schem
plugins/SinceDungeonPremium/schematics/ForgottenCrypt_Template.schematic
```

Enable schematic mode in Premium config:

```yaml
instancing:
  mode: "SCHEMATIC"
```

If `shared-world.enabled` is true, all schematic runs are pasted into isolated regions inside one shared void world.

For the complete per-dungeon schematic workflow, see [Schematic and Instancing Setup](13-schematic-instancing.md).
