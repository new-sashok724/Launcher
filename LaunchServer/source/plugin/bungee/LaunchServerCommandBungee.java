package launchserver.plugin.bungee;

import launchserver.plugin.LaunchServerPluginBridge;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.command.ConsoleCommandSender;

public final class LaunchServerCommandBungee extends Command {
    private static final BaseComponent[] NOT_CONSOLE_MESSAGE = TextComponent.fromLegacyText(ChatColor.RED + "Эту команду можно использовать только из консоли");
    private static final BaseComponent[] NOT_INITIALIZED_MESSAGE = TextComponent.fromLegacyText(ChatColor.RED + "Лаунчсервер не был полностью загружен");

    // Instance
    public final LaunchServerPluginBungee plugin;

    public LaunchServerCommandBungee(LaunchServerPluginBungee plugin) {
        super("launchserver", null, "launcher", "ls", "l");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String... args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(NOT_CONSOLE_MESSAGE);
            return;
        }

        // Eval command
        LaunchServerPluginBridge bridge = plugin.bridge;
        if (bridge == null) {
            sender.sendMessage(NOT_INITIALIZED_MESSAGE);
        } else {
            bridge.eval(args);
        }
    }
}
