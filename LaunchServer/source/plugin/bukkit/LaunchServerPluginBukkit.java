package launchserver.plugin.bukkit;

import launchserver.plugin.LaunchServerPluginBridge;
import org.bukkit.plugin.java.JavaPlugin;

public final class LaunchServerPluginBukkit extends JavaPlugin {
    public volatile LaunchServerPluginBridge bridge = null;

    @Override
    public void onDisable() {
        super.onDisable();
        if (bridge != null) {
            bridge.close();
            bridge = null;
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();

        // Initialize LaunchServer
        try {
            bridge = new LaunchServerPluginBridge(getDataFolder().toPath());
        } catch (Throwable exc) {
            exc.printStackTrace();
        }

        // Register command
        getCommand("launchserver").setExecutor(new LaunchServerCommandBukkit(this));
    }
}
