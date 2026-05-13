# Developer API

SinceDungeon exposes a public API through:

```java
SinceDungeonAPI.get()
```

## Access

```java
SinceDungeonAPI api = SinceDungeonAPI.get();
```

The API is initialized by Core during plugin startup.

## Managers

```java
api.getRewardManager();
api.getPartyManager();
api.getInstanceManager();
api.getManager();
```

## Dungeon Control

```java
api.joinDungeon(player, "dungeon_id");
api.quitDungeon(player);
api.forceStopDungeon(playerUuid, true);
api.isPlaying(player);
api.getGame(player);
api.getAllActiveGames();
```

## Templates

```java
api.registerTemplate(template);
api.unregisterTemplate("dungeon_id");
api.getTemplate("dungeon_id");
api.getAvailableTemplates();
```

## Custom Actions

Register a custom action:

```java
api.registerCustomAction(
    "CUSTOM_ACTION",
    data -> new MyAction(data),
    "Custom Action",
    Material.COMMAND_BLOCK,
    "Runs custom logic.",
    defaults,
    prompts
);
```

Unregister:

```java
api.unregisterCustomAction("CUSTOM_ACTION");
```

## Reward Processors

```java
api.registerRewardProcessor("TOKEN", (player, value, displayName) -> {
    // give custom reward
});
```

Unregister:

```java
api.unregisterRewardProcessor("TOKEN");
```

## Condition Processors

```java
api.registerConditionProcessor("CUSTOM", (player, value) -> {
    return true;
});
```

## Custom Item Providers

```java
api.registerItemProvider("TOKEN", data -> {
    return itemStack;
});
```

Dynamic item string:

```text
TOKEN:any:data:you:want
```

## Safe Item Giving

```java
api.giveItemSafely(player, itemStack, "Display Name");
```

This protects against full inventories and generated dungeon worlds.

## Events

Available events:

- `DungeonStartEvent`
- `DungeonStageCompleteEvent`
- `DungeonFinishEvent`
- `DungeonEndEvent`
- `DungeonRewardClaimEvent`

Use these events to integrate quests, statistics, custom rewards, or external systems.

## Extension Interfaces

Core interfaces:

- `RewardSystem`
- `RewardProcessor`
- `PartyProvider`
- `InstanceProvider`
- `CustomItemProvider`
- `ConditionProcessor`

These interfaces allow replacing or extending major systems without editing Core code.
