---
layout: page
title: Premium Addon
---

SinceDungeon Premium Addon extends Core with advanced actions, roulette rewards, hologram drops, hologram leaderboards,
affixes, webhooks, and MythicMobs item support.

Premium plugin:

```text
SinceDungeon-PremiumAddon
```

It requires Core:

```text
SinceDungeon
```

## Premium Commands

```text
/sdp reload
/sdp stage insert <map_id> <position>
/sdp hologram create <map_id> <category>
/sdp hologram move <hologram_id>
/sdp hologram delete <hologram_id>
```

Alias:

```text
/sdpremium
```

Permission:

```text
SinceDungeon.admin
```

## Advanced Actions

### BUFF

```yaml
type: "BUFF"
effect_type: "SPEED"
duration: 200
amplifier: 1
```

### ESCORT_NPC

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
vip_attributes:
  - "follow_range:128.0"
vip_equipment: []
attacker_mob: "ZOMBIE"
attacker_amount: 3
attacker_interval: 100
attacker_name: "&cAssassin"
attacker_is_baby: false
attacker_attributes: []
attacker_equipment: []
```

### BRANCHING_PATH

```yaml
type: "BRANCHING_PATH"
path_a_loc: "0,64,0"
path_b_loc: "10,64,10"
stage_a: 3
stage_b: 4
radius: 3.0
```

### LEVER_PUZZLE

```yaml
type: "LEVER_PUZZLE"
levers:
  - "0,64,0"
  - "2,64,0"
  - "4,64,0"
fail_time_penalty: 5
```

### CHECKPOINT

```yaml
type: "CHECKPOINT"
location: "0,64,0"
radius: 3.0
sound: "entity.player.levelup"
particle: "TOTEM_OF_UNDYING"
```

### DAMAGE_ZONE

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

```yaml
type: "JUMP_STAGE"
target_stage: 5
```

### CINEMATIC_DIALOGUE

```yaml
type: "CINEMATIC_DIALOGUE"
frames:
  - "40;&e&lThe King;&fAh, heroes.;&e[King] Ah, heroes.;entity.villager.trade"
```

Frame format:

```text
<ticks>;<title>;<subtitle>;<chat message>;<sound>
```

### PROJECTILE_TRAP

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
attacker_name: "&cInvader"
attacker_is_baby: false
attacker_attributes: []
attacker_equipment: []
```

### GIVE_ITEM

```yaml
type: "GIVE_ITEM"
item_data: "DIAMOND:1"
receive_message: "&aYou received a mysterious item..."
```

### PLAY_SOUND

```yaml
type: "PLAY_SOUND"
sound_name: "entity.ender_dragon.growl"
volume: 1.0
pitch: 1.0
```

### NPC_INTERACTION

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
npc_attributes: []
npc_equipment: []
```

## Mythic+ Affixes

Attach affixes by dungeon ID:

```yaml
affixes:
  example_dungeon:
    - "VOLCANIC"
    - "VAMPIRIC"
```

Available affixes:

- `VOLCANIC`: delayed explosion hazard.
- `VAMPIRIC`: mobs heal from damage dealt to players.

Settings:

```yaml
affixes-settings:
  volcanic:
    damage: 10.0
    radius: 3.0
    delay-ticks: 40
  vampiric:
    heal-percentage: 0.5
```

## Roulette Rewards

```yaml
roulette:
  enabled: true
  title: "&6&lReward Spin"
  gui-size: 27
  border-material: "BLACK_STAINED_GLASS_PANE"
  pointer-material: "HOPPER"
  top-pointer-slot: 4
  bottom-pointer-slot: 22
  spin-start-slot: 9
  spin-end-slot: 17
  reward-slot: 13
  next-spin-delay-ticks: 40
```

Animation settings:

```yaml
roulette:
  animation:
    frames: 60
    slow-at-frame: 40
    slower-at-frame: 50
    slowest-at-frame: 55
    slow-delay-ticks: 2
    slower-delay-ticks: 4
    slowest-delay-ticks: 8
```

## Holograms

Premium hologram leaderboards use native Minecraft `TextDisplay` entities. DecentHolograms is no longer required.

```yaml
hologram-drops:
  enabled: true

hologram-leaderboard:
  update-interval-seconds: 300
  line-spacing: 0.28
  view-range: 64.0
  locations:
```

Use commands to create leaderboard holograms in-game:

```text
/sdp hologram create example_dungeon FASTEST_TIME
```

## Webhooks

```yaml
webhooks:
  enabled: true
  url: "YOUR_DISCORD_WEBHOOK_URL_HERE"
  embed-title: "Dungeon Cleared!"
  embed-color: "5814783"
  embed-description: "Map: **%dungeon%**\nTime: **%time%s**\nPlayers: **%players%**"
```

## Premium Item Integration

Premium registers MythicMobs items as both item providers and rewards:

```text
MYTHIC_ITEM:<internal_name>:<amount>
```

Reward example:

```yaml
rewards:
  pool:
    mythic_reward:
      type: "MYTHIC_ITEM"
      value: "AncientRelic:1"
      chance: 5.0
      name: "<gold>Ancient Relic"
```

## Schematic Instancing

Premium can switch Core's instance provider to schematic mode when WorldEdit or FastAsyncWorldEdit is installed:

```yaml
instancing:
  mode: "SCHEMATIC"
```

See [Folia and Schematic Instancing](folia-schematic.md) for full setup notes.
