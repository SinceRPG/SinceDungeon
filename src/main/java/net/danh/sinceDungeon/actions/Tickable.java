package net.danh.sinceDungeon.actions;

import net.danh.sinceDungeon.manager.DungeonGame;

/**
 * Interface indicating that an action requires tick-based updates.
 */
public interface Tickable {
    /**
     * Called continuously during the dungeon tick cycle.
     *
     * @param game The current dungeon game.
     */
    void onTick(DungeonGame game);
}