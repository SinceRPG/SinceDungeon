package net.danh.sinceDungeon.actions;

import net.danh.sinceDungeon.manager.DungeonGame;

public interface Tickable {
    void onTick(DungeonGame game);
}