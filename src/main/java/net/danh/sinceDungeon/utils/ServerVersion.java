package net.danh.sinceDungeon.utils;

import org.bukkit.Bukkit;

public class ServerVersion {
    private static int major = 0;
    private static int minor = 0;
    private static int patch = 0;

    private static String nmsVersion = "";
    private static boolean isPaper = false;
    private static boolean isFolia = false;

    static {
        // 1. Phân tích Semantic Version siêu chuẩn từ getBukkitVersion()
        // Dữ liệu mẫu: "1.21.11-R0.1-SNAPSHOT" hoặc "26.1.0-R0.1" (Trong tương lai)
        try {
            String versionString = Bukkit.getBukkitVersion().split("-")[0]; // Lấy "1.21.11"
            String[] parts = versionString.split("\\.");

            if (parts.length > 0) major = Integer.parseInt(parts[0]);
            if (parts.length > 1) minor = Integer.parseInt(parts[1]);
            if (parts.length > 2) patch = Integer.parseInt(parts[2]);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[SinceDungeon] Không thể phân tích phiên bản máy chủ!");
        }

        // 2. Lấy NMS Version string (Dành cho 1.8 -> 1.20.4 Spigot)
        // Lưu ý: Từ Paper 1.20.5+, nó sẽ trả về "craftbukkit" (không còn v1_20_R4)
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            nmsVersion = packageName.substring(packageName.lastIndexOf('.') + 1);
        } catch (Exception e) {
            nmsVersion = "UNKNOWN";
        }

        // 3. Kiểm tra nền tảng (PaperMC)
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

        // 4. Kiểm tra nền tảng đa luồng Folia (Các server to bây giờ rất hay dùng)
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException ignored) {
        }
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

    public static String getNmsVersion() {
        return nmsVersion;
    }

    public static boolean isPaper() {
        return isPaper;
    }

    public static boolean isFolia() {
        return isFolia;
    }

    /**
     * Kiểm tra tương lai (Hỗ trợ cấu trúc VD: 26.1.0)
     * VD: isAtLeast(26, 1, 0) -> Kiểm tra xem có từ bản 26.1.0 trở lên không
     * VD: isAtLeast(1, 21, 11) -> Kiểm tra xem có từ bản 1.21.11 trở lên không
     */
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

    /**
     * Kiểm tra xem phiên bản có NHỎ HƠN HOẶC BẰNG mức yêu cầu không.
     * VD: isAtMost(1, 21, 10) -> True nếu là 1.21.10 trở xuống
     */
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

    /**
     * Dùng tắt cho chuẩn Minecraft 1.x hiện hành.
     * VD: isAtLeast(21) -> True nếu là 1.21.0 trở lên
     */
    public static boolean isAtLeast(int reqMinor) {
        return isAtLeast(1, reqMinor, 0);
    }

    /**
     * Dùng tắt cho chuẩn Minecraft 1.x hiện hành (Tới Patch).
     * VD: isAtLeast(20, 4) -> True nếu là 1.20.4 trở lên
     */
    public static boolean isAtLeast(int reqMinor, int reqPatch) {
        return isAtLeast(1, reqMinor, reqPatch);
    }

    /**
     * Kiểm tra chính xác 1 phiên bản cụ thể
     * VD: isExactly(1, 21, 11) -> Chỉ chạy khi đúng là bản 1.21.11
     */
    public static boolean isExactly(int reqMajor, int reqMinor, int reqPatch) {
        return major == reqMajor && minor == reqMinor && patch == reqPatch;
    }
}