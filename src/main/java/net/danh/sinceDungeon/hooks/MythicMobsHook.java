package net.danh.sinceDungeon.hooks;

import org.bukkit.entity.Entity;

/**
 * Lớp trung gian cách ly API của MythicMobs.
 * Đảm bảo Máy ảo Java (JVM) không bị Crash nếu Server không cài MythicMobs.
 */
public class MythicMobsHook {

    public static boolean isMythicMob(Entity entity) {
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getMythicMobInstance(entity) != null;
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }
}