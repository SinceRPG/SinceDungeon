package net.danh.sinceDungeon.utils;

import org.bukkit.Bukkit;

/**
 * Identifies the running server software version, logic, and variants.
 */
public class ServerVersion {
    private static int major = 0;
    private static int minor = 0;
    private static int patch = 0;

    private static String nmsVersion = "";
    private static int revisionNumber = 0;

    private static boolean isPaper = false;
    private static boolean isFolia = false;

    static {
        try {
            String versionString = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = versionString.split("\\.");

            if (parts.length > 0) major = Integer.parseInt(parts[0]);
            if (parts.length > 1) minor = Integer.parseInt(parts[1]);
            if (parts.length > 2) patch = Integer.parseInt(parts[2]);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[SinceDungeon] Cannot parse server build version!");
        }

        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            isPaper = true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("io.papermc.paper.configuration.PaperConfigurations");
                isPaper = true;
            } catch (ClassNotFoundException ignored) {
            }
        }

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException ignored) {
        }

        revisionNumber = findRevisionNumber();

        if (revisionNumber != 0) {
            nmsVersion = "v" + major + "_" + minor + "_R" + revisionNumber;
        } else {
            nmsVersion = "craftbukkit";
        }
    }

    private static int findRevisionNumber() {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String revString = packageName.split("\\.")[3];
            return Integer.parseInt(revString.split("_")[2].replaceAll("[^0-9]", ""));
        } catch (Throwable ignored) {
        }

        for (int i = 1; i <= 10; i++) {
            try {
                String candidate = "v" + major + "_" + minor + "_R" + i;
                Class.forName("org.bukkit.craftbukkit." + candidate + ".CraftServer");
                return i;
            } catch (Throwable ignored) {
            }
        }

        return 0;
    }

    public static int getMajor() {
        return major;
    }

    public static int getMinor() {
        return minor;
    }

    public static int getPatch() {
        return patch;
    }

    /**
     * Gets the full NMS revision representation format.
     *
     * @return NMS structure string.
     */
    public static String getNmsVersion() {
        return nmsVersion;
    }

    public static int getRevisionNumber() {
        return revisionNumber;
    }

    public static boolean isPaper() {
        return isPaper;
    }

    public static boolean isFolia() {
        return isFolia;
    }

    public static boolean isAtLeast(int reqMajor, int reqMinor, int reqPatch) {
        if (major > reqMajor) return true;
        if (major == reqMajor) {
            if (minor > reqMinor) return true;
            if (minor == reqMinor) {
                return patch >= reqPatch;
            }
        }
        return false;
    }

    public static boolean isAtMost(int reqMajor, int reqMinor, int reqPatch) {
        if (major < reqMajor) return true;
        if (major == reqMajor) {
            if (minor < reqMinor) return true;
            if (minor == reqMinor) {
                return patch <= reqPatch;
            }
        }
        return false;
    }

    public static boolean isAtLeast(int reqMinor) {
        return isAtLeast(1, reqMinor, 0);
    }

    public static boolean isAtLeast(int reqMinor, int reqPatch) {
        return isAtLeast(1, reqMinor, reqPatch);
    }

    public static boolean isExactly(int reqMajor, int reqMinor, int reqPatch) {
        return major == reqMajor && minor == reqMinor && patch == reqPatch;
    }
}