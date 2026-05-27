---
layout: page
title: Commands
---
Command roots are configurable in Core `config.yml`.

Default roots:

- `/dungeon`, aliases `/dg`, `/inst`
- `/party`, aliases `/p`, `/pt`
- `/sincedungeon`, aliases `/sincedungeonpremium`, `/sd`, `/sdungeon`
- Premium: `/sdp`, alias `/sdpremium`

Tab completion respects dungeon visibility. Members only see `public: true` dungeons. Admins with `SinceDungeon.admin` see both public and private dungeons.

## Player Dungeon Commands

| Command | Description |
| --- | --- |
| `/dungeon join <name>` | Join a dungeon. Members can only join public dungeons; admins can join private dungeons for testing. |
| `/dungeon join <name> <target>` | Admin or console starts a dungeon for another player, including private dungeons. |
| `/dungeon leave` | Leave the current dungeon. |
| `/dungeon lives` | Check your lives. |
| `/dungeon top <map>` | Open the top GUI for a dungeon. |
| `/dungeon revive <target>` | Revive a spectator teammate using a Soul Crystal. |

## Admin Dungeon Commands

Requires `SinceDungeon.admin`.

| Command | Description |
| --- | --- |
| `/dungeon editor` | Open the in-game editor. |
| `/dungeon spectate <target>` | Spectate a player currently inside a dungeon. |
| `/dungeon getkey <id>` | Give yourself a dungeon key item. |

## Party Commands

The built-in party command is available only when the default party provider is active.

| Command | Description |
| --- | --- |
| `/party` | Show party usage. |
| `/party create` | Create a party. |
| `/party disband` | Disband your party as leader. |
| `/party invite <target>` | Invite another player. |
| `/party accept <leader>` | Accept a party invite. |
| `/party leave` | Leave your party. |
| `/party promote <target>` | Transfer party leadership. |
| `/party kick <target>` | Remove a member. |
| `/party chat` | Toggle party chat. |
| `/party list` | List party members. |

## Core Admin Commands

Requires `SinceDungeon.admin`.

| Command | Description |
| --- | --- |
| `/sincedungeon reload` | Reload plugin files. |
| `/sincedungeon stage insert <map_id> <position>` | Insert and shift dungeon stages. |
| `/sincedungeon top reset <map>` | Reset a map leaderboard. |
| `/sincedungeon top resetplayer <target>` | Reset a player's leaderboard entries across all maps. |
| `/sincedungeon top resetplayer <target> <map>` | Reset a player's leaderboard entries for one map. |

## Lives Admin Commands

| Command | Description |
| --- | --- |
| `/sincedungeon lives <target> add <amount> [-s]` | Add current lives. |
| `/sincedungeon lives <target> reduce <amount> [-s]` | Reduce current lives. |
| `/sincedungeon lives <target> set <amount> [-s]` | Set current lives. |
| `/sincedungeon lives <target> addmax <amount> [-s]` | Add max lives. |
| `/sincedungeon lives <target> setregenamount <amount> [-s]` | Set custom regen amount. |
| `/sincedungeon lives <target> setregeninterval <seconds> [-s]` | Set custom regen interval. |
| `/sincedungeon lives <target> resetregen [-s]` | Reset custom regen settings. |
| `/sincedungeon lives <target> check [-s]` | Check lives. |
| `/sincedungeon givelifeitem <target> <amount> [-s]` | Give a Soul Crystal item. |

## Cooldown Admin Commands

| Command | Description |
| --- | --- |
| `/sincedungeon cooldown check <target> <map>` | Check a player's cooldown. |
| `/sincedungeon cooldown reset <target> <map>` | Reset one dungeon cooldown. |
| `/sincedungeon cooldown resetall <target>` | Reset all cooldowns. |
| `/sincedungeon cooldown reduce <target> <seconds>` | Reduce all cooldowns. |
| `/sincedungeon givecooldownitem reset <target> <amount>` | Give reset tickets. |
| `/sincedungeon givecooldownitem reduce <target> <amount> <seconds>` | Give reduction tickets. |

## Premium Commands

Requires `SinceDungeon.admin`.

| Command | Description |
| --- | --- |
| `/sdp reload` | Reload Premium config and holograms. |
| `/sdp stage insert <map_id> <position>` | Insert and shift dungeon stages. |
| `/sdp hologram create <map_id> <category>` | Create a leaderboard hologram at your location. |
| `/sdp hologram move <hologram_id>` | Move an existing hologram to your location. |
| `/sdp hologram delete <hologram_id>` | Delete a hologram. |

Premium hologram categories:

- `FASTEST_TIME`
- `PARTY_FASTEST_TIME`
- `MOST_KILLS`
- `MOST_CLEARS`
