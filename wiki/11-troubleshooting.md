# Troubleshooting

## Server freezes after provider overwrite logs

Symptoms:

```text
[SinceDungeon] [API] Reward System overwritten by: DefaultRewardSystem
[SinceDungeon] [API] Party System overwritten by: DefaultPartyProvider
[SinceDungeon] [API] Instancing System overwritten by: DefaultInstanceProvider
```

Cause:

- Startup was blocked while waiting for async template loading.

Fix:

- Use a build that loads database, cooldowns, lives, and templates asynchronously.
- Check logs for `[Startup] SinceDungeon finished loading data.`
- Increase `startup.async-timeout-seconds` if needed.

## Dungeon says data is still loading

Message:

```text
Dungeon data is still loading. Please try again in a moment.
```

Cause:

- A player tried to join before startup data finished loading.

Fix:

- Wait for startup ready log.
- Check database connectivity.
- Check template YAML errors.

## Schematic not found

Check:

```yaml
template-world: "MyDungeon"
```

Required file:

```text
plugins/SinceDungeonPremium/schematics/MyDungeon.schem
```

or:

```text
plugins/SinceDungeonPremium/schematics/MyDungeon.schematic
```

## Premium schematic mode does not enable

Check:

- Premium is installed.
- Core is installed.
- WorldEdit or FastAsyncWorldEdit is installed.
- `instancing.mode` is `SCHEMATIC`.

## Placeholder conditions always fail

Check:

- PlaceholderAPI is installed.
- The placeholder returns a value.
- Condition syntax is correct.

Example:

```yaml
check: "%player_level%;>=;10"
```

## MMOItems rewards fail

Check:

- MMOItems is installed.
- Item type and ID are correct.
- Use the correct format:

```text
MMOITEMS:<type>:<id>:<amount>
```

or reward:

```yaml
type: "MMOITEM"
value: "SWORD:SILVER_LANCE:1"
```

## Redis cross-server mode does not work

Check:

- Redis server is reachable.
- Every server uses the same `redis.channel`.
- Every server has a unique `cross-server.server-name`.
- Proxy server names match `return-server` and target server names.
- BungeeCord or Velocity plugin channel matches `cross-server.bungee-channel`.

## Players get stuck in generated worlds

The plugin has ghost rescue behavior. Check:

- Multiverse-Inventories bypass permissions.
- `settings.mvi-bypass-permissions`.
- Whether the generated world folder can be deleted by the server process.

## Database fails to initialize

Check:

- SQLite file permissions.
- MySQL host, port, database, username, password.
- Hikari pool timeout.
- Server firewall.

If database startup fails, the plugin disables itself instead of running half-loaded.

## GUI editor input does nothing

Check:

- Player has `SinceDungeon.admin`.
- Player is not typing commands while in input mode.
- Type `cancel` to leave input mode.

## Advanced YAML path is not added

Use:

```text
path=value
```

Example:

```text
settings.max-players=8
```

Do not type only the path.
