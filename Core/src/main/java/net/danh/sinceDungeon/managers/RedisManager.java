package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.systems.party.DefaultPartyProvider;
import net.danh.sinceDungeon.systems.party.DefaultPartyProvider.Party;
import net.danh.sinceDungeon.utils.SchedulerCompat;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the Redis Pub/Sub connection for cross-server dungeon matchmaking.
 * Safely hooks into the DefaultPartyProvider to synchronize party states across proxy nodes.
 * Automatically handles reconnection logic and thread-safe Bukkit API dispatching.
 */
public class RedisManager {
    private final SinceDungeon plugin;
    private final String channelName;
    private final String localServerName;
    private JedisPool jedisPool;
    private JedisPubSub pubSub;
    private SchedulerCompat.TaskHandle listenerTask;

    public RedisManager(SinceDungeon plugin) {
        this.plugin = plugin;
        this.channelName = plugin.getConfigFile().getString("redis.channel", "SinceDungeon");
        this.localServerName = plugin.getConfigFile().getString("cross-server.server-name", "dungeon-node-1");
    }

    public void connect() {
        String host = plugin.getConfigFile().getString("redis.host", "localhost");
        int port = plugin.getConfigFile().getInt("redis.port", 6379);
        String password = plugin.getConfigFile().getString("redis.password", "");
        int timeoutMillis = plugin.getConfigFile().getInt("redis.timeout-millis", 2000);
        int maxTotal = plugin.getConfigFile().getInt("redis.pool.max-total", 10);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);

        if (password != null && !password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, timeoutMillis, password);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, timeoutMillis);
        }

        try {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }
        } catch (Exception e) {
            jedisPool.close();
            jedisPool = null;
            throw e;
        }

        plugin.getLogger().info(plugin.getLanguageManager().getString("admin.log.redis_connected", "[Redis] Connected to Redis successfully!"));
        startListening();
    }

    public CompletableFuture<Void> connectAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SchedulerCompat.runAsync(plugin, () -> {
            try {
                connect();
                future.complete(null);
            } catch (Exception e) {
                plugin.getLogger().warning(plugin.getLanguageManager().getString("admin.log.redis_connect_error", "[Redis] Failed to connect: ") + e.getMessage());
                future.complete(null);
            }
        });
        return future;
    }

    public void disconnect() {
        if (pubSub != null) {
            pubSub.unsubscribe();
        }
        if (listenerTask != null && !listenerTask.isCancelled()) {
            listenerTask.cancel();
        }
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            plugin.getLogger().info(plugin.getLanguageManager().getString("admin.log.redis_disconnected", "[Redis] Redis connection closed."));
        }
    }

    /**
     * Initializes an asynchronous thread to listen for incoming Redis Pub/Sub messages.
     * Incorporates an infinite while-loop with sleep-backoff to automatically reconnect if the Redis server restarts.
     */
    private void startListening() {
        int reconnectDelayMillis = plugin.getConfigFile().getInt("redis.reconnect-delay-millis", 5000);
        listenerTask = SchedulerCompat.runAsync(plugin, () -> {
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
                    plugin.getLogger().info(plugin.getLanguageManager().getString("admin.log.redis_connected", "[Redis] Ready to receive Cross-server messages."));
                    jedis.subscribe(pubSub, channelName);
                } catch (Exception e) {
                    plugin.getLogger().severe(plugin.getLanguageManager().getString("admin.log.redis_listen_error", "[Redis] Listener error/disconnected: ") + e.getMessage());
                    plugin.getLogger().info(plugin.getLanguageManager().getString("admin.log.redis_reconnect", "[Redis] Trying to reconnect after 5 seconds..."));
                    try {
                        Thread.sleep(reconnectDelayMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
    }

    public void publishMessage(String message) {
        if (jedisPool == null || jedisPool.isClosed()) {
            plugin.getLogger().warning(plugin.getLanguageManager().getString("admin.log.redis_publish_unavailable", "[Redis] Cannot publish because Redis is not connected."));
            return;
        }

        SchedulerCompat.runAsync(plugin, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(channelName, message);
            } catch (Exception e) {
                plugin.getLogger().warning(plugin.getLanguageManager().getString("admin.log.redis_publish_error", "[Redis] Cannot publish message: ") + e.getMessage());
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

    /**
     * Parses incoming Pub/Sub messages and delegates internal logic.
     * Safely pushes Bukkit API calls back to the main server thread to prevent concurrent modification exceptions.
     * Utilizes instanceof to ensure safe execution when the Native Party System is active.
     *
     * @param message The raw string payload received from Redis.
     */
    private void handleMessage(String message) {
        String[] parts = message.split(":");
        if (parts.length < 2) return;

        String action = parts[0];

        if (action.equals("REQUEST_SERVER") && parts.length >= 3) {
            String templateId = parts[1];
            UUID leaderUuid = UUID.fromString(parts[2]);
            String partyDataRaw = parts.length > 3 ? parts[3] : "";

            if (plugin.getDungeonManager().getTemplates().containsKey(templateId)) {
                SchedulerCompat.runGlobal(plugin, () -> {
                    if (!partyDataRaw.isEmpty()) {
                        String[] members = partyDataRaw.split(",");
                        if (plugin.getPartyManager().getProvider() instanceof DefaultPartyProvider defaultParty) {
                            defaultParty.forceCreateCrossServerParty(leaderUuid, members);
                        }
                    }
                    plugin.getDungeonManager().addPendingCrossServerGame(leaderUuid, templateId);
                    replyDungeonReady(leaderUuid);
                });
            }
        } else if (action.equals("SERVER_READY") && parts.length >= 3) {
            UUID leaderUuid = UUID.fromString(parts[1]);
            String targetServer = parts[2];

            SchedulerCompat.runGlobal(plugin, () -> {
                plugin.getDungeonManager().handleCrossServerReady(leaderUuid, targetServer);
            });
        } else if (action.equals("PARTY_DISBAND")) {
            UUID leaderUuid = UUID.fromString(parts[1]);
            SchedulerCompat.runGlobal(plugin, () -> {
                if (plugin.getPartyManager().getProvider() instanceof DefaultPartyProvider defaultParty) {
                    Party party = defaultParty.getPartyObject(leaderUuid);
                    if (party != null) {
                        defaultParty.silentDisband(party);
                    }
                }
            });
        } else if (action.equals("PARTY_LEAVE")) {
            UUID targetUuid = UUID.fromString(parts[1]);
            SchedulerCompat.runGlobal(plugin, () -> {
                if (plugin.getPartyManager().getProvider() instanceof DefaultPartyProvider defaultParty) {
                    defaultParty.silentQuit(targetUuid);
                }
            });
        }
    }
}
