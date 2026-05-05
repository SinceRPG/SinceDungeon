package net.danh.sinceDungeon.api.interfaces;

import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

/**
 * Interface defining the strict contract for Dungeon World Instancing.
 * Implementing this allows developers to replace standard Bukkit world generation
 * with advanced, lag-free solutions like SlimeWorldManager or AdvancedSlimePaper.
 */
public interface InstanceProvider {

    void initialize();

    void cleanup();

    /**
     * Asynchronously creates a new world instance from a template.
     *
     * @param templateName The name of the source template world/folder.
     * @param instanceId   The generated unique ID for the new instance.
     * @return A CompletableFuture returning the loaded World.
     */
    CompletableFuture<World> createInstance(String templateName, String instanceId);

    /**
     * Safely unloads and permanently deletes the given world instance.
     *
     * @param world The world to delete.
     */
    void unloadAndDeleteInstance(World world);

    /**
     * Forcefully unloads and deletes the world, ignoring active players (used for server shutdown).
     *
     * @param world The world to delete.
     */
    void forceUnloadAndDeleteInstance(World world);
}