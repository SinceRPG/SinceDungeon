package net.danh.sinceDungeon.system;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.World;

public class WorldGuardHook {
    public static void applyDungeonFlags(World world) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(world));
            if (regions == null) return;

            GlobalProtectedRegion global = new GlobalProtectedRegion("__global__");

            // Chặn đập phá, đặt block, pvp
            global.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
            global.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
            global.setFlag(Flags.PVP, StateFlag.State.DENY);

            // Chặn vụ nổ phá hoại kiến trúc/mạch của dungeon
            global.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
            global.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
            global.setFlag(Flags.GHAST_FIREBALL, StateFlag.State.DENY);

            // Cho phép mở rương, dùng cửa/nút
            global.setFlag(Flags.USE, StateFlag.State.ALLOW);
            global.setFlag(Flags.CHEST_ACCESS, StateFlag.State.ALLOW);

            regions.addRegion(global);
        } catch (NoClassDefFoundError | Exception ignored) {
            // Bỏ qua nếu không cài WorldGuard
        }
    }
}