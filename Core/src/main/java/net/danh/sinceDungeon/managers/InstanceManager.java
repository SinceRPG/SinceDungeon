package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.interfaces.InstanceProvider;

/**
 * Manages the active InstanceProvider implementation for SinceDungeon.
 * Follows the Strategy Design Pattern.
 */
public class InstanceManager {

    private final SinceDungeon plugin;
    private InstanceProvider activeProvider;

    public InstanceManager(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    public InstanceProvider getProvider() {
        return activeProvider;
    }

    public void setProvider(InstanceProvider provider) {
        if (this.activeProvider != null) {
            this.activeProvider.cleanup();
        }
        this.activeProvider = provider;
        this.activeProvider.initialize();

        String logMsg = plugin.getLanguageManager().getString("admin.log.instance_system_set", "[API] Instancing System overwritten by: <system>");
        plugin.getLogger().info(logMsg.replace("<system>", provider.getClass().getSimpleName()));
    }
}