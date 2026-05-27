---
layout: page
title: Developer API
---

SinceDungeon exposes `SinceDungeonAPI` for third-party plugins.

```java
SinceDungeonAPI api = SinceDungeonAPI.get();
```

## Dungeon Control

```java
api.joinDungeon(player, "example_dungeon");
api.joinDungeon(player, "private_test_dungeon", true);
api.quitDungeon(player);
api.forceStopDungeon(player.getUniqueId(), true);

boolean playing = api.isPlaying(player);
DungeonGame game = api.getGame(player);
Map<UUID, DungeonGame> games = api.getAllActiveGames();
```

The two-argument `joinDungeon` follows normal visibility rules: private dungeons require the player to have
`SinceDungeon.admin`. The three-argument overload can explicitly allow private dungeons for controlled integrations.

## Templates

```java
DungeonTemplate template = api.getTemplate("example_dungeon");
Set<String> ids = api.getAvailableTemplates();

api.registerTemplate(template);
api.unregisterTemplate("example_dungeon");
```

## Managers

```java
RewardManager rewards = api.getRewardManager();
PartySystemManager parties = api.getPartyManager();
InstanceManager instances = api.getInstanceManager();
DungeonManager dungeonManager = api.getManager();
```

## Custom Actions

```java
api.registerCustomAction(
    "CUSTOM_ACTION",
    map -> new MyCustomAction(),
    "Custom Action",
    Material.NETHER_STAR,
    "Runs my custom objective.",
    defaultParams,
    customPrompts
);
```

The action parser receives the action YAML values as a map.

## Custom Reward Processors

```java
api.registerRewardProcessor("TOKENS", (player, value, displayName) -> {
    int amount = Integer.parseInt(value);
    // Give tokens here
});
```

Dungeon YAML:

```yaml
type: "TOKENS"
value: "100"
name: "<gold>100 Tokens"
```

## Custom Condition Processors

```java
api.registerConditionProcessor("MY_CHECK", (player, value) -> {
    return player.hasPermission(value);
});
```

## Custom Item Providers

```java
api.registerItemProvider("MYITEM", data -> {
    // data includes the full string, for example MYITEM:starter_sword:1
    return itemStack;
});
```

This works in loot chests, custom drops, and other item-data consumers.

## Safe Item Delivery

```java
api.giveItemSafely(player, itemStack, "Reward Name");
```

This avoids losing items when inventories are full or when players are inside temporary dungeon worlds.

## Events

### DungeonStartEvent

Called before a dungeon instance is generated and players are teleported.

Useful methods:

- `getInitiator()`
- `getTemplate()`
- `getParticipants()`
- `setCancelled(boolean)`

The participant set is mutable.

### DungeonStageCompleteEvent

Called when a stage is completed.

- `getGame()`
- `getStageIndex()`

### DungeonFinishEvent

Called when a dungeon is completed.

- `getGame()`
- `getCompletionTimeSeconds()`
- `getChestCount()`
- `setChestCount(int)`

Use this event to modify reward chest count.

### DungeonRewardClaimEvent

Called when a player claims a reward.

- `getPlayer()`
- `getReward()`
- `setReward(DungeonReward)`
- `setCancelled(boolean)`

### DungeonEndEvent

Called when a dungeon session ends for any reason.

- `getGame()`
- `getReason()`

Reasons:

- `CLEARED`
- `FAILED`
- `FORCE_STOPPED`

## Extension Interfaces

| Interface            | Purpose                            |
|----------------------|------------------------------------|
| `PartyProvider`      | Replace the built-in party system. |
| `InstanceProvider`   | Replace default world instancing.  |
| `RewardSystem`       | Replace reward distribution.       |
| `RewardProcessor`    | Add reward types.                  |
| `ConditionProcessor` | Add condition types.               |
| `CustomItemProvider` | Add item-data formats.             |

## Version

```java
String version = api.getPluginVersion();
```
