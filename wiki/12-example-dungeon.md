# Example Dungeon

This is a compact example showing the main systems together.

```yaml
template-world: "ForgottenCrypt_Template"
public: true

settings:
  max-players: 6
  required-lives-to-join: 1
  lives-deducted-per-death: 1
  keep-inventory-on-death: true
  prevent-item-dropping: true
  block-ender-pearls: true
  kick-delay-after-finish: 15
  force-daylight-and-clear-weather: true
  save-and-restore-stats: true
  death-action: "RESPAWN"
  clear-mob-drops: true
  randomize-stages: false
  cooldown-seconds: 1800
  cooldown-on-leave: true
  commands:
    on-start:
      - "lp user %player% permission set dungeon.in_game true"
    on-finish:
      - "lp user %player% permission unset dungeon.in_game"
    on-first-finish:
      - "eco give %player% 5000"

conditions:
  level_check:
    name: "Level Requirement"
    check: "%player_level%;>=;10"
    msg: "<red>You must be at least Level 10 to enter this dungeon!"

rewards:
  solo-tiers:
    300: 3
    600: 2
    1200: 1
  party-tiers:
    240: 3
    500: 2
    1000: 1
  pool:
    diamonds:
      type: "ITEM"
      value: "DIAMOND:3-8"
      chance: 50.0
      name: "<yellow>Diamonds"
    cash:
      type: "COMMAND"
      value: "eco give %player% 1000"
      chance: 100.0
      name: "<green>$1,000 Cash"

stages:
  1:
    chance: 100.0
    actions:
      arrival:
        type: "REACH_LOCATION"
        target: "10,64,10"
        radius: 3.0
        start_message:
          - "<yellow>Move to the crypt entrance."

  2:
    chance: 100.0
    actions:
      guards:
        type: "SPAWN_WAVE"
        mob: "SKELETON"
        amount: 5
        scale_with_party: true
        custom_name: "<red>Crypt Guard"
        locations:
          - "15,64,20"
          - "5,64,20"
        start_message:
          - "<red>The dead are rising!"

  3:
    chance: 100.0
    actions:
      key_chest:
        type: "LOOT_CHEST"
        location: "20,60,-5"
        items:
          13: "KEY:gate_key:1"
          14: "GOLD_INGOT:5"
      gate:
        type: "UNLOCK_DOOR"
        key_id: "gate_key"
        trigger: "0,64,-10"
        corner1: "-2,64,-10"
        corner2: "2,68,-10"
        particle: "SOUL_FIRE_FLAME"

  4:
    chance: 100.0
    actions:
      boss:
        type: "BOSS_BATTLE"
        location: "0,64,-50"
        mob: "ZOMBIE"
        custom_name: "&4&lCrypt Lord"
        base_health: 500.0
        scale_health_per_player: 150.0
        bar_color: "RED"
        bar_style: "SOLID"
        attributes:
          - "movement_speed:0.3"
        phases:
          50:
            message: "&cThe Crypt Lord calls reinforcements!"
            reinforcements:
              mob: "ZOMBIE"
              amount: 3
              custom_name: "&cCrypt Minion"
              attributes: []
              equipment: []
```

For Premium schematic mode, place:

```text
plugins/SinceDungeonPremium/schematics/ForgottenCrypt_Template.schem
```
