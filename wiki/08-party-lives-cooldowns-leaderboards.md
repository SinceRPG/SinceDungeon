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
  lives-deducted-on-leave: 0
  lives-deducted-on-fail: 0
  lives-deducted-on-clear: 0
```

Lives regenerate based on global config unless overridden per player.

`lives-deducted-per-death` is charged for every death during a run. The three outcome settings are charged when a
player leaves or disconnects, the run fails, or the run clears. These can stack with per-death losses already taken.
Set an outcome cost to `0` to disable that specific penalty.

## Cooldowns

Dungeon cooldowns are stored in the database.

Template fields:

```yaml
settings:
  cooldown-seconds: 1800
  cooldown-on-leave: true
```

`cooldown-on-leave` applies cooldowns to leave/disconnect/fail outcomes. It does not control the outcome life costs;
use the `lives-deducted-on-*` settings for those.

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
Premium leaderboard holograms are rendered with native TextDisplay entities, so DecentHolograms is not required.
