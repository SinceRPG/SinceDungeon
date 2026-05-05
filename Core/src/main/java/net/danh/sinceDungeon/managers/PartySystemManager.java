package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.interfaces.PartyProvider;

/**
 * Manages the active PartyProvider implementation for SinceDungeon.
 * Follows the Strategy Design Pattern.
 */
public class PartySystemManager {

    private final SinceDungeon plugin;
    private PartyProvider activeProvider;

    public PartySystemManager(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    public PartyProvider getProvider() {
        return activeProvider;
    }

    public void setProvider(PartyProvider provider) {
        if (this.activeProvider != null) {
            this.activeProvider.cleanup();
        }
        this.activeProvider = provider;
        this.activeProvider.initialize();

        String logMsg = plugin.getLanguageManager().getString("admin.log.party_system_set", "[API] Party System overwritten by: <system>");
        plugin.getLogger().info(logMsg.replace("<system>", provider.getClass().getSimpleName()));
    }
}