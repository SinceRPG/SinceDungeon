package net.danh.sinceDungeon.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;

public class WorldUtils {

    private static final ArrayList<String> IGNORE_FILES = new ArrayList<>(Arrays.asList("uid.dat", "session.lock"));

    public static boolean copyWorld(File source, File target) {
        if (!source.exists()) return false;
        try {
            Files.walkFileTree(source.toPath(), EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path targetDir = target.toPath().resolve(source.toPath().relativize(dir));
                    try {
                        Files.createDirectories(targetDir);
                    } catch (FileAlreadyExistsException e) {
                        if (!Files.isDirectory(targetDir)) throw e;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (IGNORE_FILES.contains(file.getFileName().toString())) {
                        return FileVisitResult.CONTINUE;
                    }
                    Files.copy(file, target.toPath().resolve(source.toPath().relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // [TỐI ƯU CỰC MẠNH]: Cố gắng xóa mọi file có thể, bỏ qua ngoại lệ cục bộ để không hủy ngang tiến trình
    public static boolean deleteWorld(File path) {
        if (!path.exists()) return true;
        try {
            Files.walkFileTree(path.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                    } catch (IOException ignored) {
                        // Bỏ qua các file cứng đầu bị OS khóa tạm thời (thường là session.lock)
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        Files.delete(dir);
                    } catch (IOException ignored) {
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return !path.exists();
        } catch (IOException e) {
            return false;
        }
    }
}