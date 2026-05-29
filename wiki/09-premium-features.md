# Premium Features

SinceDungeon Premium extends Core with advanced actions, schematic instancing, reward presentation, affixes, webhooks,
and holograms.

## Premium Requirements

- SinceDungeon Core
- WorldEdit or FastAsyncWorldEdit for schematic mode
- Optional MythicMobs
- Native TextDisplay holograms are built in; DecentHolograms is not required

## Schematic Instancing

Premium can replace the Core world-folder clone provider with a schematic provider.

```yaml
instancing:
  mode: "SCHEMATIC"
  paste-y-level: 64
  schematic:
    paste-air: true
```

Schematic files go in:

```text
plugins/SinceDungeon-PremiumAddon/schematics/
```

The file name is based on dungeon `template-world`.

## Shared World Mode

```yaml
instancing:
  schematic:
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

In shared-world mode:

- One void world is kept loaded.
- Each dungeon run receives an isolated region.
- Actions use normal YAML coordinates and are offset internally.
- The region can be cleared when the run ends.

## Premium Actions

### BUFF

Applies a potion effect.

```yaml
type: "BUFF"
effect_type: "SPEED"
duration: 200
amplifier: 1
```

### ESCORT_NPC

Protects an NPC from start to target.

```yaml
type: "ESCORT_NPC"
entity_type: "VILLAGER"
custom_name: "&aVIP Escort"
max_health: 100.0
start_location: "0,64,0"
target_location: "20,64,20"
speed: 1.0
success_radius: 4.0
vip_is_baby: false
attacker_mob: "ZOMBIE"
attacker_amount: 3
attacker_interval: 100
```

### BRANCHING_PATH

Routes players to one of two stages.

```yaml
type: "BRANCHING_PATH"
path_a_loc: "0,64,0"
path_b_loc: "10,64,10"
stage_a: 3
stage_b: 4
radius: 3.0
```

### LEVER_PUZZLE

Requires levers to be activated in order.

```yaml
type: "LEVER_PUZZLE"
levers:
  - "0,64,0"
  - "2,64,0"
fail_time_penalty: 5
```

### CHECKPOINT

Updates respawn/checkpoint location.

```yaml
type: "CHECKPOINT"
location: "0,64,0"
radius: 3.0
sound: "entity.player.levelup"
particle: "TOTEM_OF_UNDYING"
```

### DAMAGE_ZONE

Creates an area hazard.

```yaml
type: "DAMAGE_ZONE"
location: "0,64,0"
radius: 5.0
damage: 4.0
interval: 20
duration: 200
particle: "CAMPFIRE_COSY_SMOKE"
```

### JUMP_STAGE

Skips to another stage.

```yaml
type: "JUMP_STAGE"
target_stage: 5
```

### CINEMATIC_DIALOGUE

Plays a sequence of dialogue frames.

```yaml
type: "CINEMATIC_DIALOGUE"
frames:
  - "40;&e&lThe King;&fAh, heroes.;&e[King] Ah, heroes.;entity.villager.trade"
```

### PROJECTILE_TRAP

Fires projectiles repeatedly.

```yaml
type: "PROJECTILE_TRAP"
location: "0,64,0"
direction: "0,-1,0"
projectile_type: "ARROW"
interval: 20
speed: 1.5
duration: 200
```

### DEFEND_CORE

Protects an entity from attackers.

```yaml
type: "DEFEND_CORE"
location: "0,64,0"
core_type: "IRON_GOLEM"
core_name: "&b&lSacred Crystal"
core_health: 1000.0
duration: 600
attacker_mob: "ZOMBIE"
attacker_amount: 5
attacker_interval: 100
```

### GIVE_ITEM

Gives an item directly.

```yaml
type: "GIVE_ITEM"
item_data: "DIAMOND:1"
receive_message: "&aYou received an item."
```

### PLAY_SOUND

Plays a sound to participants.

```yaml
type: "PLAY_SOUND"
sound_name: "entity.ender_dragon.growl"
volume: 1.0
pitch: 1.0
```

### NPC_INTERACTION

Spawns an interactive NPC for dialogue, movement, hand-ins, rewards, and teleporting.

```yaml
type: "NPC_INTERACTION"
entity_type: "VILLAGER"
custom_name: "&eDungeon Guide"
npc_location: "0,64,0"
target_location: "5,64,5"
interaction_mode: "TALK"
message_scope: "PLAYER"
teleport_scope: "PLAYER"
max_health: 40.0
move_speed: 1.0
success_radius: 2.5
interaction_radius: 4.0
start_on_click: true
npc_is_baby: false
consume_required_item: true
fail_on_npc_death: true
click_cooldown_ticks: 20
dialogue_lines:
  - "&e[NPC] &fHello, <player>."
required_item: "NONE"
reward_item: "NONE"
reward_display_name: ""
npc_attributes: [ ]
npc_equipment: [ ]
```

## Affixes

Configure affixes per dungeon:

```yaml
affixes:
  example_dungeon:
    - "VOLCANIC"
    - "VAMPIRIC"
```

Available affixes:

- `VOLCANIC`
- `VAMPIRIC`

## Roulette Rewards

```yaml
roulette:
  enabled: true
  title: "&6&lReward Spin"
```

If enabled, rewards are presented through a roulette animation.

## Hologram Drops

```yaml
hologram-drops:
  enabled: true
```

Shows reward drops with holographic presentation.

## Hologram Leaderboards

```yaml
hologram-leaderboard:
  update-interval-seconds: 300
  line-spacing: 0.28
  view-range: 64.0
  locations: { }
```

Premium commands can create, move, and delete leaderboard holograms.
Leaderboard holograms are native `TextDisplay` entities. They are refreshed on the configured interval, centered toward
viewers, and cleaned up/re-rendered by the Premium hologram manager.

## Discord Webhooks

```yaml
webhooks:
  enabled: true
  url: "YOUR_DISCORD_WEBHOOK_URL_HERE"
  embed-title: "Dungeon Cleared!"
  embed-color: "5814783"
  embed-description: "Map: **%dungeon%**\nTime: **%time%s**\nPlayers: **%players%**"
  connect-timeout-ms: 5000
  read-timeout-ms: 5000
```

Webhooks are sent when dungeons are completed.
