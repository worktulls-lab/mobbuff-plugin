package com.example.mobbuff;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class MobBuffCommand implements CommandExecutor {

    private final MobBuffPlugin plugin;

    public MobBuffCommand(MobBuffPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(ChatColor.YELLOW + "Использование: /mobbuff <on|off|status>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on" -> {
                if (plugin.isBuffEnabled()) {
                    sender.sendMessage(ChatColor.GRAY + "Баф мобов уже включён.");
                } else {
                    plugin.setBuffEnabled(true);
                    sender.sendMessage(ChatColor.GREEN + "Баф мобов включён: здоровье и урон x2.");
                }
            }
            case "off" -> {
                if (!plugin.isBuffEnabled()) {
                    sender.sendMessage(ChatColor.GRAY + "Баф мобов уже выключен.");
                } else {
                    plugin.setBuffEnabled(false);
                    sender.sendMessage(ChatColor.RED + "Баф мобов выключен, здоровье возвращено к обычному.");
                }
            }
            case "status" -> sender.sendMessage(ChatColor.AQUA + "Баф мобов сейчас: "
                    + (plugin.isBuffEnabled() ? ChatColor.GREEN + "ВКЛЮЧЁН" : ChatColor.RED + "ВЫКЛЮЧЕН"));
            default -> sender.sendMessage(ChatColor.YELLOW + "Использование: /mobbuff <on|off|status>");
        }
        return true;
    }
}
