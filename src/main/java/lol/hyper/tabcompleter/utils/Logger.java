package lol.hyper.tabcompleter.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

import lol.hyper.tabcompleter.TabCompleter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Logger {
    private static final ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();

    private static Component createMessage(String msg, NamedTextColor color) {
        return Component.text("[")
                .color(NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("TabCompleter")
                        .color(NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text("] ")
                        .color(NamedTextColor.WHITE)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text(msg).color(color).decorate(TextDecoration.BOLD));
    }

    public static void log(Component msg) {
        console.sendMessage(msg);
    }

    public static void info(String msg) {
        console.sendMessage(createMessage(msg, NamedTextColor.WHITE));
    }

    public static void warn(String msg) {
        console.sendMessage(createMessage(msg, NamedTextColor.YELLOW));
    }

    public static void error(String msg) {
        console.sendMessage(createMessage(msg, NamedTextColor.RED));
    }

    public static void error(Exception e) {
        // Message
        console.sendMessage(createMessage("Exception: " + e.getMessage(), NamedTextColor.RED));

        // Stack trace
        for (StackTraceElement element : e.getStackTrace()) {
            Component stackTraceLine = Component.text("\tat " + element.toString())
                    .color(NamedTextColor.GRAY);
            console.sendMessage(stackTraceLine);
        }
    }

    public static void debug(String msg) {
        boolean isDebug = TabCompleter.getInstance().getConfig().getBoolean("config.debug-logger", false);

        if (isDebug) {
            console.sendMessage(createMessage(msg, NamedTextColor.BLUE));
        }
    }
}