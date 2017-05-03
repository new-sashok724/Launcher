package launchserver.plugin.bukkit;

import launchserver.plugin.LaunchServerPluginBridge;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;

public final class LaunchServerCommandBukkit implements CommandExecutor {
    public final LaunchServerPluginBukkit plugin;

    public LaunchServerCommandBukkit(LaunchServerPluginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String... args) {
        if (!(sender instanceof ConsoleCommandSender) && !(sender instanceof RemoteConsoleCommandSender)) {
            sender.sendMessage(ChatColor.RED + "Эту команду можно использовать только из консоли");
            return true;
        }

        // Eval command
        LaunchServerPluginBridge bridge = plugin.bridge;
        if (bridge == null) {
            sender.sendMessage(ChatColor.RED + "Лаунчсервер не был полностью загружен");
        } else {
            bridge.eval(args);
        }
        return true;
    }
}
