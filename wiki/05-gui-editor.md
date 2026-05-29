# GUI Editor

Open the editor with:

```text
/dungeon editor
```

Required permission:

```text
SinceDungeon.admin
```

## Main Menu

The main editor menu lists dungeon YAML files from:

```text
plugins/SinceDungeon/dungeons/
```

Actions:

- Left Click a dungeon file to edit it.
- Shift Right Click a dungeon file to delete it.
- Click Create New Dungeon to create a new YAML file.

## Dungeon Menu

The dungeon menu edits the main dungeon template.

Main sections:

- World Template
- Public Status
- Entry Conditions
- Gameplay Settings
- Rewards
- Stages
- Advanced YAML
- Save
- Delete Dungeon

## World Template

World mode:

- Set this to the world folder name.

Schematic mode:

- Set this to the schematic file base name.

Example:

```yaml
template-world: "ForgottenCrypt_Template"
```

Schematic file:

```text
plugins/SinceDungeon-PremiumAddon/schematics/ForgottenCrypt_Template.schem
```

## Gameplay Settings Menu

Edits map-level settings, including:

- Inventory behavior
- Ender pearl blocking
- Kick delay
- Weather control
- Save and restore stats
- Death action
- Mob drop clearing
- Required lives
- Lives deducted on death
- Randomized stages
- Max players
- Cooldown
- Cooldown on leave
- Required item
- Required item consumption
- Start/finish/first-finish commands

## Conditions Menu

Manage entry conditions:

- Add condition
- Edit condition check
- Edit fail message
- Delete condition

## Rewards Menu

Manage:

- Solo reward tiers
- Party reward tiers
- Reward pool entries

Reward entries support:

- Reward type
- Reward value
- Chance
- Display name
- Lore

## Stages Menu

Manage stages:

- Add stage
- Insert stage
- Edit stage chance
- Edit stage commands
- Open stage actions
- Delete stage

## Actions Menu

Inside a stage, the Actions menu allows:

- Adding any registered action type
- Editing all action fields
- Editing list fields
- Editing location fields
- Setting locations from current player position
- Editing loot chest item slots
- Editing notifications
- Editing boss phases and reinforcements
- Deleting actions

Core actions and Premium actions appear automatically when registered.

## Advanced YAML Menu

The Advanced YAML editor exists for full A-Z control over a dungeon file.

It lists every leaf YAML path in the dungeon file and allows:

- Left Click: edit value through chat
- Right Click on boolean fields: toggle
- Right Click on location fields: set current player position
- Shift Right Click: delete the path
- List fields: open the list editor
- Add YAML path: create or overwrite a raw path

Add path format:

```text
path=value
```

Examples:

```text
settings.max-players=8
settings.cooldown-seconds=3600
stages.2.actions.wave.amount=12
stages.2.actions.wave.scale_with_party=true
```

Use Advanced YAML for new fields, custom extension fields, or data that does not yet have a dedicated GUI button.

## Saving

Click Save Changes in the dungeon menu.

Saving:

- Writes the YAML file.
- Reloads the dungeon template.
- Keeps the editor session active.

## Input Mode

When the editor asks for chat input:

- Type the value in chat.
- Type `cancel` to abort.
- For locations, type `here` to use your current position.
- For block positions, right-click a block while in location input mode.
