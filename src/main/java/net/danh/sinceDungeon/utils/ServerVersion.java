package net.danh.sinceDungeon.utils;

import org.bukkit.Bukkit;

public class ServerVersion {
    private static int major = 0;
    private static int minor = 0;
    private static int patch = 0;

    private static String nmsVersion = "";
    private static int revisionNumber = 0; // Thêm từ MythicLib

    private static boolean isPaper = false;
    private static boolean isFolia = false;

    static {
        // ==========================================
        // 1. LẤY PHIÊN BẢN THEO CHUẨN SEMANTIC (Major.Minor.Patch)
        // ==========================================
        try {
            String versionString = Bukkit.getBukkitVersion().split("-")[0]; // VD: "1.21.1"
            String[] parts = versionString.split("\\.");

            if (parts.length > 0) major = Integer.parseInt(parts[0]);
            if (parts.length > 1) minor = Integer.parseInt(parts[1]);
            if (parts.length > 2) patch = Integer.parseInt(parts[2]);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[SinceDungeon] Không thể phân tích phiên bản máy chủ!");
        }

        // ==========================================
        // 2. KIỂM TRA NỀN TẢNG (Paper & Folia)
        // ==========================================
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

        // ==========================================
        // 3. LOGIC LẤY NMS REVISION CỦA MYTHICLIB (Tích hợp mới)
        // ==========================================
        revisionNumber = findRevisionNumber();

        if (revisionNumber != 0) {
            // Cấu trúc lại chuỗi NMS chuẩn (VD: v1_20_R4)
            nmsVersion = "v" + major + "_" + minor + "_R" + revisionNumber;
        } else {
            // Dành cho Paper 1.20.5+ (Đã loại bỏ hoàn toàn hệ thống Revision)
            nmsVersion = "craftbukkit";
        }
    }

    /**
     * Thuật toán tìm Revision Number mượn từ MythicLib.
     * Xử lý được cả thay đổi breaking changes của Spigot 1.20.5+.
     */
    private static int findRevisionNumber() {
        // Cách 1: Dành cho Spigot / Paper < 1.20.5 (Dựa vào tên package)
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String revString = packageName.split("\\.")[3]; // VD lấy ra "v1_20_R4"
            // Tách chữ R và lấy số (4)
            return Integer.parseInt(revString.split("_")[2].replaceAll("[^0-9]", ""));
        } catch (Throwable ignored) {
        }

        // Cách 2: Dành cho Spigot 1.20.5+ (Brute-force quét class CraftServer)
        for (int i = 1; i <= 10; i++) {
            try {
                String candidate = "v" + major + "_" + minor + "_R" + i;
                Class.forName("org.bukkit.craftbukkit." + candidate + ".CraftServer");
                return i;
            } catch (Throwable ignored) {
            }
        }

        // CÁch 3: Paper 1.20.5+ (Không còn dùng Revision Number nữa)
        return 0;
    }

    // ==========================================
    // CÁC HÀM GETTER VÀ KIỂM TRA PHIÊN BẢN (Giữ nguyên của bạn)
    // ==========================================

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
     * Trả về tên phiên bản NMS (VD: "v1_20_R4" hoặc "craftbukkit")
     */
    public static String getNmsVersion() {
        return nmsVersion;
    }

    /**
     * Trả về số Revision (VD: bản R4 thì trả về 4)
     */
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