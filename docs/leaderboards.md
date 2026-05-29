---
layout: page
title: Leaderboards
---

SinceDungeon stores leaderboard data in the configured database and displays it through GUIs, PlaceholderAPI, and
Premium holograms.

## Categories

| Category             | Meaning                                      |
|----------------------|----------------------------------------------|
| `FASTEST_TIME`       | Fastest solo or credited player clear times. |
| `PARTY_FASTEST_TIME` | Fastest party clear times.                   |
| `MOST_KILLS`         | Highest kill counts.                         |
| `MOST_CLEARS`        | Most dungeon clears.                         |

## Top Award Mode

```yaml
dungeon:
  top-awarded-to: "ALL_MEMBERS"
```

Modes:

- `ALL_MEMBERS`: every completed party member receives credit.
- `LEADER_ONLY`: only the party leader receives credit.

## Player GUI

```text
/dungeon top <map>
```

Only public maps are suggested to players.

## Admin Reset Commands

```text
/sincedungeon top reset <map>
/sincedungeon top resetplayer <target>
/sincedungeon top resetplayer <target> <map>
```

## GUI Settings

```yaml
leaderboard:
  fetch-limit: 50
  gui-size: 54
  date-format: "dd/MM/yyyy HH:mm"
  items:
    rank_1: GOLD_BLOCK
    rank_2: IRON_BLOCK
    rank_3: COPPER_BLOCK
    rank_other: COAL_BLOCK
    category_time: CLOCK
    category_kills: DIAMOND_SWORD
    category_clears: NETHER_STAR
    category_party_time: GOLDEN_APPLE
```

## PlaceholderAPI

Top placeholders use:

```text
%sincedungeontop_<category>_<map_id>_<rank>_<type>%
```

Example:

```text
%sincedungeontop_fastest_example_dungeon_1_name%
%sincedungeontop_fastest_example_dungeon_1_value%
```

Top placeholder categories:

- `fastest`
- `partyfastest`
- `kills`
- `clears`

Types:

- `name`
- `value`

## Premium Hologram Leaderboards

Premium displays leaderboard holograms with native `TextDisplay` entities. No DecentHolograms installation is required.

```text
/sdp hologram create <map_id> <category>
/sdp hologram move <hologram_id>
/sdp hologram delete <hologram_id>
```

Update interval:

```yaml
hologram-leaderboard:
  update-interval-seconds: 300
  line-spacing: 0.28
  view-range: 64.0
```
