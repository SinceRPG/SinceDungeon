# Party, Lives, Cooldowns, and Leaderboards

## Party System

The built-in party provider supports:

- Party creation
- Invites
- Accept/deny
- Leave
- Disband
- Kick
- Party chat
- Friendly fire control
- Cross-server party synchronization when Redis mode is enabled

Dungeon joining with parties:

- Only the leader can start a party dungeon.
- Offline members are left behind.
- Dead members are left behind.
- Members too far away can be left behind depending on configured max distance.
- The dungeon checks cooldowns, lives, max players, required items, and conditions for all participants.

## Lives System

Lives are stored in the database and cached in memory.

Per-player fields:

- Current lives
- Max lives
- Custom regeneration amount
- Custom regeneration interval
- Last regeneration timestamp

Dungeon template fields:

```yaml
settings:
  required-lives-to-join: 1
  lives-deducted-per-death: 1
```

Lives regenerate based on global config unless overridden per player.

## Cooldowns

Dungeon cooldowns are stored in the database.

Template fields:

```yaml
settings:
  cooldown-seconds: 1800
  cooldown-on-leave: true
```

Cooldown tools:

- Reset one dungeon cooldown.
- Reset all cooldowns for a player.
- Reduce all cooldowns by seconds.
- Give cooldown reset item.
- Give cooldown reduce item.

## Leaderboards

Tracked categories:

- Fastest solo clear
- Fastest party clear
- Most kills
- Most clears

Tables:

- `top_fastest`
- `party_top_fastest`
- `top_kills`
- `top_clears`

Players with this permission are ignored:

```text
SinceDungeon.top.ignore
```

## Top GUI

The top GUI displays leaderboard records from the database. Public dungeons can be shown in public lists and top menus.
