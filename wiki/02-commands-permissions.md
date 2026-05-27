# Commands and Permissions

Command names and aliases are configurable in `config.yml`.

## Default Commands

```yaml
commands:
  party: "party"
  party-aliases:
    - "p"
    - "pt"
  dungeon: "dungeon"
  dungeon-aliases:
    - "dg"
    - "inst"
  admin: "sincedungeon"
  admin-aliases:
    - "sincedungeonpremium"
    - "sd"
    - "sdungeon"
```

## Dungeon Commands

```text
/dungeon lives
/dungeon top <dungeon>
/dungeon editor
/dungeon join <dungeon>
/dungeon join <dungeon> <player>
/dungeon leave
/dungeon revive <player>
/dungeon spectate <player>
/dungeon getkey <key-id>
```

Typical usage:

- `/dungeon lives` shows the player's current lives.
- `/dungeon top crypt` opens leaderboard views.
- `/dungeon editor` opens the GUI editor.
- `/dungeon join crypt` starts a dungeon.
- `/dungeon join crypt Steve` forces a target player into a dungeon and requires `SinceDungeon.admin`.
- `/dungeon leave` leaves the current dungeon.
- `/dungeon revive Steve` consumes a life item to revive a knocked-out spectator in the same dungeon.
- `/dungeon spectate Steve` lets an admin spectate an active dungeon player.
- `/dungeon getkey gate_key` gives the admin a dungeon key item.

## Admin Commands

The default admin root command is `/sincedungeon` and can be changed with `commands.admin`.

```text
/sincedungeon reload
/sincedungeon stage insert <dungeon> <position>
/sincedungeon top reset <dungeon>
/sincedungeon top resetplayer <player>
/sincedungeon top resetplayer <player> <dungeon>
/sincedungeon lives <player> add <amount> [-s]
/sincedungeon lives <player> reduce <amount> [-s]
/sincedungeon lives <player> set <amount> [-s]
/sincedungeon lives <player> addmax <amount> [-s]
/sincedungeon lives <player> setregenamount <amount> [-s]
/sincedungeon lives <player> setregeninterval <seconds> [-s]
/sincedungeon lives <player> resetregen [-s]
/sincedungeon lives <player> check [-s]
/sincedungeon givelifeitem <player> <amount> [-s]
/sincedungeon cooldown check <player> <dungeon>
/sincedungeon cooldown reset <player> <dungeon>
/sincedungeon cooldown resetall <player>
/sincedungeon cooldown reduce <player> <seconds>
/sincedungeon givecooldownitem reset <player> <amount>
/sincedungeon givecooldownitem reduce <player> <amount> <seconds>
```

`/sincedungeon stage insert` shifts existing numeric stages down and creates a blank stage at the requested position.

## Premium Commands

Premium adds management commands for Premium systems, including:

```text
/sdp reload
/sdp stage insert <dungeon> <position>
/sdp hologram create <dungeon> <category>
/sdp hologram move <hologram-id>
/sdp hologram delete <hologram-id>
```

Alias:

```text
/sdpremium
```

Hologram categories:

```text
FASTEST_TIME
PARTY_FASTEST_TIME
MOST_KILLS
MOST_CLEARS
```

## Party Commands

The party command supports normal party workflows:

```text
/party
/party create
/party invite <player>
/party accept <player>
/party leave
/party disband
/party promote <player>
/party kick <player>
/party chat
/party list
```

`/party invite <player>` automatically creates a party if the inviter is not already in one.

## Permissions

Main permission:

```text
SinceDungeon.admin
```

Leaderboard ignore permission:

```text
SinceDungeon.top.ignore
```

Multiverse rescue bypass permissions are configurable:

```yaml
settings:
  mvi-bypass-permissions:
    - "mvinv.bypass.*"
    - "Multiverse-Inventories.bypass.*"
```
