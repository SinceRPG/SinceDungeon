# Schematic and Instancing Setup

This page explains how to set up one dungeon with either Core world-folder instancing or Premium schematic instancing.

## Instancing Modes

SinceDungeon has two instance providers:

- `WORLD`: Core default behavior. The plugin clones a full template world folder for every run.
- `SCHEMATIC`: Premium behavior. The plugin creates or reuses a void world and pastes a `.schem` or `.schematic` file
  for every run.

Core registers the default world provider. Premium overwrites the instance provider when schematic mode is enabled and
the required WorldEdit hook is available.

## World Folder Mode

Use this mode when each dungeon template is a complete world folder.

1. Build the dungeon in a normal world.
2. Stop the server or make sure the world is safely saved.
3. Put the template world folder where Bukkit can load it as a world.
4. Set the dungeon file:

```yaml
template-world: "ForgottenCrypt_Template"
```

5. Start the dungeon with:

```text
/dungeon join forgotten_crypt
```

The plugin creates a temporary instance world for the run and removes it when the run ends.

## Premium Schematic Mode

Use this mode when each dungeon is a schematic file.

1. Install Core and Premium.
2. Install WorldEdit or FastAsyncWorldEdit.
3. Enable schematic mode in `plugins/SinceDungeonPremium/config.yml`:

```yaml
instancing:
  mode: "SCHEMATIC"
  paste-y-level: 64
  schematic:
    paste-air: true
```

4. Put the schematic in:

```text
plugins/SinceDungeonPremium/schematics/
```

5. Set the dungeon file:

```yaml
template-world: "ForgottenCrypt_Template"
```

6. Use one of these file names:

```text
plugins/SinceDungeonPremium/schematics/ForgottenCrypt_Template.schem
plugins/SinceDungeonPremium/schematics/ForgottenCrypt_Template.schematic
```

`template-world` is the base file name without the extension.

## Shared World Schematic Mode

Shared world mode pastes every run into a separated region inside one void world.

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

Field behavior:

- `name`: shared void world name.
- `spawn-location`: safe spawn point inside the shared world.
- `region-spacing`: distance between dungeon regions.
- `region-radius`: clear radius around each assigned region.
- `grid-width`: number of regions per grid row before moving to the next row.
- `clear-on-release`: clears the used region when the run ends.
- `clear-min-y` and `clear-max-y`: vertical clear bounds.

Dungeon action coordinates still use the original schematic coordinates. The Premium provider offsets them internally
into the assigned shared-world region.

## Per-Dungeon Setup Workflow

1. Create the schematic or template world.
2. Create `plugins/SinceDungeon/dungeons/<dungeon-id>.yml`.
3. Set `template-world` to the world folder name or schematic base name.
4. Run `/dungeon editor`.
5. Open the dungeon file.
6. Set `public`, `max-players`, lives, cooldowns, death action, and commands.
7. Add conditions if the dungeon requires level, permission, quest progress, or another PlaceholderAPI check.
8. Add reward tiers and reward pool entries.
9. Add stages in the order players should complete them.
10. Add one or more actions to each stage.
11. Use the Advanced YAML editor for fields that do not have a dedicated GUI button.
12. Save the dungeon.
13. Run `/<admin> reload` or `/sdp reload` if Premium config changed.
14. Test with `/dungeon join <dungeon-id>`.

## Coordinate Rules

Use simple coordinate strings:

```text
x,y,z
```

Examples:

```yaml
target: "10,64,10"
location: "0,70,0"
corner1: "-2,64,-10"
corner2: "2,68,-10"
```

In the GUI editor, location fields can be filled from the player's current position or by right-clicking a block while
the editor is waiting for location input.

## Action Setup Order

Use actions as small objectives. A clean dungeon normally uses one objective per action and one or more actions per
stage.

Common setup patterns:

- Entrance checkpoint: `REACH_LOCATION`
- Combat room: `SPAWN_WAVE`, `RANDOM_WAVE`, or `MYTHIC_WAVE`
- Locked path: `LOOT_CHEST` gives `KEY:<id>:1`, then `UNLOCK_DOOR` consumes that key
- Hazard room: Premium `DAMAGE_ZONE` or `PROJECTILE_TRAP`
- Story moment: Premium `CINEMATIC_DIALOGUE` or `NPC_INTERACTION`
- Final room: `BOSS_BATTLE` or Premium `DEFEND_CORE`

## Testing Checklist

Before releasing a dungeon:

- The server log shows SinceDungeon startup is ready.
- The dungeon appears in `/dungeon editor`.
- The schematic or world folder name matches `template-world`.
- All stage numbers are numeric and ordered.
- Every action has a valid `type`.
- All locations are inside the schematic or template world area.
- The reward pool has at least one valid reward.
- Conditions return the expected PlaceholderAPI values.
- `/dungeon join <dungeon-id>` starts without console errors.
- The instance world or shared-world region is cleaned after the run.
