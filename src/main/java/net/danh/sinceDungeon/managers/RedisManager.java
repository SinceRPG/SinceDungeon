package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import org.bukkit.Bukkit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class RedisManager {
    private final SinceDungeon plugin;
    private final String channelName;
    private final String localServerName;
    private JedisPool jedisPool;
    private JedisPubSub pubSub;

    public RedisManager(SinceDungeon plugin) {
        this.plugin = plugin;
        this.channelName = plugin.getConfigFile().getString("redis.channel", "SinceDungeon");
        this.localServerName = plugin.getConfigFile().getString("cross-server.server-name", "dungeon-node-1");
    }

    public void connect() {
        String host = plugin.getConfigFile().getString("redis.host", "localhost");
        int port = plugin.getConfigFile().getInt("redis.port", 6379);
        String password = plugin.getConfigFile().getString("redis.password", "");

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);

        if (password != null && !password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, 2000);
        }

        plugin.getLogger().info(plugin.getMessagesFile().getString("admin.log.redis_connected", "[Redis] Connected to Redis successfully!"));
        startListening();
    }

    public void disconnect() {
        if (pubSub != null) {
            pubSub.unsubscribe();
        }
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            plugin.getLogger().info(plugin.getMessagesFile().getString("admin.log.redis_disconnected", "[Redis] Redis connection closed."));
        }
    }

    private void startListening() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            while (!Thread.currentThread().isInterrupted() && jedisPool != null && !jedisPool.isClosed()) {
                try (Jedis jedis = jedisPool.getResource()) {
                    pubSub = new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            if (channel.equals(channelName)) {
                                handleMessage(message);
                            }
                        }
                    };
                    plugin.getLogger().info(plugin.getMessagesFile().getString("admin.log.redis_connected", "[Redis] Ready to receive Cross-server messages."));
                    jedis.subscribe(pubSub, channelName);
                } catch (Exception e) {
                    plugin.getLogger().severe(plugin.getMessagesFile().getString("admin.log.redis_listen_error", "[Redis] Listener error/disconnected: ") + e.getMessage());
                    plugin.getLogger().info("[Redis] Trying to reconnect after 5 seconds...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
    }

    public void publishMessage(String message) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(channelName, message);
            } catch (Exception e) {
                plugin.getLogger().warning(plugin.getMessagesFile().getString("admin.log.redis_publish_error", "[Redis] Cannot publish message: ") + e.getMessage());
            }
        });
    }

    public void syncDisbandParty(UUID leaderUuid) {
        publishMessage("PARTY_DISBAND:" + leaderUuid.toString());
    }

    public void syncLeaveParty(UUID targetUuid) {
        publishMessage("PARTY_LEAVE:" + targetUuid.toString());
    }

    public void requestDungeonServer(String templateId, UUID leaderUuid, String partyDataRaw) {
        publishMessage("REQUEST_SERVER:" + templateId + ":" + leaderUuid.toString() + ":" + partyDataRaw);
    }

    public void replyDungeonReady(UUID leaderUuid) {
        publishMessage("SERVER_READY:" + leaderUuid.toString() + ":" + localServerName);
    }

    private void handleMessage(String message) {
        String[] parts = message.split(":");
        if (parts.length < 2) return;

        String action = parts[0];

        if (action.equals("REQUEST_SERVER") && parts.length >= 3) {
            String templateId = parts[1];
            UUID leaderUuid = UUID.fromString(parts[2]);
            String partyDataRaw = parts.length > 3 ? parts[3] : "";

            if (plugin.getDungeonManager().getTemplates().containsKey(templateId)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!partyDataRaw.isEmpty()) {
                        String[] members = partyDataRaw.split(",");
                        plugin.getPartyManager().forceCreateCrossServerParty(leaderUuid, members);
                    }
                    plugin.getDungeonManager().addPendingCrossServerGame(leaderUuid, templateId);
                    replyDungeonReady(leaderUuid);
                });
            }
        } else if (action.equals("SERVER_READY") && parts.length >= 3) {
            UUID leaderUuid = UUID.fromString(parts[1]);
            String targetServer = parts[2];

            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getDungeonManager().handleCrossServerReady(leaderUuid, targetServer);
            });
        }
        // --- XỬ LÝ ĐỒNG BỘ PARTY ---
        else if (action.equals("PARTY_DISBAND")) {
            UUID leaderUuid = UUID.fromString(parts[1]);
            Bukkit.getScheduler().runTask(plugin, () -> {
                PartyManager.Party party = plugin.getPartyManager().getParty(leaderUuid);
                if (party != null) {
                    plugin.getPartyManager().silentDisband(party);
                }
            });
        } else if (action.equals("PARTY_LEAVE")) {
            UUID targetUuid = UUID.fromString(parts[1]);
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getPartyManager().silentQuit(targetUuid);
            });
        }
    }
}