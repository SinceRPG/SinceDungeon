package net.danh.sinceDungeon.utils;

import net.danh.sinceDungeon.SinceDungeon;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class BungeeUtils {
    public static void sendPlayerToServer(Player player, String serverName) {
        String channel = SinceDungeon.getPlugin().getConfigFile().getString("cross-server.bungee-channel", "BungeeCord");
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(SinceDungeon.getPlugin(), channel, b.toByteArray());
        } catch (IOException e) {
            String logMsg = SinceDungeon.getPlugin().getLanguageManager().getString("admin.log.bungee_error", "Error when switching players <player> to server <server>");
            SinceDungeon.getPlugin().getLogger().warning(logMsg.replace("<player>", player.getName()).replace("<server>", serverName));
        }
    }
}