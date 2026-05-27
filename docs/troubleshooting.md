---
layout: page
title: Troubleshooting
---

## Dungeon Does Not Appear in Tab Completion

Check:

- The dungeon file exists in `plugins/SinceDungeon/dungeons/`.
- The filename is `<id>.yml`.
- `public: true` for regular members. Admins with `SinceDungeon.admin` can tab-complete private dungeons too.
- `template-world` is present.
- The server has been restarted or the plugin files have been reloaded.

## Member Cannot Join a Dungeon

If the dungeon has `public: false`, this is expected. Private dungeons are blocked for regular members and are intended
for admin testing or hidden content.

## Dungeon Fails to Start

Check:

- The template world folder exists.
- The player has enough lives.
- Entry conditions pass.
- The dungeon is not on cooldown.
- `max-players` is not exceeded.
- Required item settings are valid.

## Mythic Actions Do Not Spawn Mobs

Check:

- MythicMobs is installed and enabled.
- The internal MythicMob name is correct.
- The location chunk can load.
- The mob level is valid.

## Rewards Are Missing

Check:

- `rewards.solo-tiers` or `rewards.party-tiers` is configured.
- Completion time fits a tier.
- `rewards.pool` contains valid entries.
- Reward `type` matches a registered processor.
- MMOItems or MythicMobs item dependencies are installed if needed.

## Players Lose Items

Check:

- `save-and-restore-stats`.
- `keep-inventory-on-death`.
- Multiverse Inventories bypass permission settings.
- Whether inventory overflow drops at the saved location.

## Commands Are Blocked Inside Dungeons

Check:

```yaml
dungeon:
  gameplay:
    block-commands: true
    allowed-commands:
      - "/party"
      - "/dungeon"
```

Add any command that should remain usable during dungeon runs.

## Cross-Server Mode Does Not Work

Cross-server mode is experimental. Check:

- `cross-server.enabled: true`.
- Redis host, port, password, and channel match on all servers.
- Proxy server names match the proxy config.
- `bungee-channel` matches your proxy type.
- Plugin messaging is available.

## Holograms Do Not Appear, Premium

Check:

- DecentHolograms is installed.
- Premium is enabled after Core.
- The hologram was created with `/sdp hologram create`.
- The configured category is valid.
- Database contains leaderboard entries.

## Folia Dungeon Instances Fail to Create

Folia cannot create or load Bukkit worlds at runtime. Use Premium `SCHEMATIC` mode with
`instancing.schematic.shared-world.enabled: true`, and preload the configured shared world before the plugin starts.

## Schematic Dungeon Is Empty

Check:

- WorldEdit or FastAsyncWorldEdit is installed.
- `instancing.mode: "SCHEMATIC"`.
- The schematic file exists in `plugins/SinceDungeon-PremiumAddon/schematics/`.
- The schematic filename matches the dungeon `template-world`.
- On Folia, the shared world is already loaded.
