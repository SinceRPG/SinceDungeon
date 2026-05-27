# Changelog

## Unreleased

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
