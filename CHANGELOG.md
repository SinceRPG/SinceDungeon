# Changelog

## Unreleased

- Added proactive Folia validation for Core template-world/world-copy dungeons during template load, join, and editor
  save, with guidance to use Premium `SCHEMATIC` shared-world mode.
- Documented outcome-based life costs (`lives-deducted-on-leave`, `lives-deducted-on-fail`,
  `lives-deducted-on-clear`) and clarified their interaction with per-death costs and cooldown-on-leave.
- Updated Premium hologram docs for native TextDisplay leaderboard holograms and added default line spacing/view range
  config keys.

### Fixed

- Enforced private dungeon visibility: regular members cannot join `public: false` dungeons, while admins can
  tab-complete and join them for testing.
- Routed command rewards through `SchedulerCompat` so reward command execution no longer calls the Bukkit scheduler
  directly.
- Replaced raw `printStackTrace()` calls with contextual plugin logger output.
- Closed default config resource streams after auto-update checks.
- Cleared reward session cleanup task references during shutdown to avoid stale static task handles.
- Added missing Premium hologram message keys used by `HologramManager`.
- Hardened Premium hologram updates by snapshotting config on the server thread, fetching leaderboard data
  asynchronously, and rendering holograms on the owning location scheduler.

### Added

- Added API overload `joinDungeon(Player, String, boolean)` for controlled private dungeon joins by integrations.
- Documented Folia-compatible Premium schematic shared-world setup.
- Documented Premium `NPC_INTERACTION` action.
- Added GitHub Pages wiki under `docs/` with deployment workflow.

### Notes

- Folia cannot create or load Bukkit worlds at runtime. For Folia deployments, use Premium `SCHEMATIC` mode with
  shared-world enabled and preload the configured shared world before plugin startup.
