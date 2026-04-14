package dev.anticheat.commands;

import dev.anticheat.AntiCheatPlugin;
import dev.anticheat.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AntiCheatCommand implements CommandExecutor, TabCompleter {

    private final AntiCheatPlugin plugin;

    public AntiCheatCommand(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&cAntiCheat&8] ");

        if (!sender.hasPermission("anticheat.admin")) {
            sender.sendMessage(ColorUtil.colorize(prefix + plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, prefix);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(ColorUtil.colorize(prefix + plugin.getConfig().getString("messages.reload", "&aReloaded.")));
            }

            case "status" -> {
                sender.sendMessage(ColorUtil.colorize(plugin.getConfig().getString("messages.status-header", "&8--- &cAntiCheat Status &8---")));
                sender.sendMessage(ColorUtil.colorize("&7Version: &f" + plugin.getDescription().getVersion()));
                sender.sendMessage(ColorUtil.colorize("&7Fly VL Threshold: &f" + plugin.getConfig().getInt("settings.fly-vl-threshold", 10)));
                sender.sendMessage(ColorUtil.colorize("&7Ban Duration: &f" + plugin.getConfig().getInt("ban.duration-days", 30) + " days"));
                sender.sendMessage(ColorUtil.colorize("&7Debug Mode: &f" + plugin.getConfig().getBoolean("debug.enabled", false)));
                sender.sendMessage(ColorUtil.colorize("&7Tracked Players: &f" + Bukkit.getOnlinePlayers().size()));
            }

            case "whitelist" -> {
                if (args.length < 3) {
                    sender.sendMessage(ColorUtil.colorize(prefix + "&cUsage: /anticheat whitelist <add|remove> <player>"));
                    return true;
                }
                handleWhitelist(sender, args, prefix);
            }

            case "vl" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.colorize(prefix + "&cUsage: /anticheat vl <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtil.colorize(prefix + "&cPlayer not found."));
                    return true;
                }
                int vl = plugin.getViolationManager().getFlyVL(target.getUniqueId());
                sender.sendMessage(ColorUtil.colorize(prefix + "&f" + target.getName() + " &7Fly VL: &c" + vl));
            }

            default -> sendHelp(sender, prefix);
        }

        return true;
    }

    private void handleWhitelist(CommandSender sender, String[] args, String prefix) {
        String sub    = args[1].toLowerCase();
        String name   = args[2];
        Player target = Bukkit.getPlayer(name);

        String addMsg    = plugin.getConfig().getString("messages.whitelist-add",    "&aAdded {player} to bypass whitelist.");
        String removeMsg = plugin.getConfig().getString("messages.whitelist-remove", "&cRemoved {player} from bypass whitelist.");

        switch (sub) {
            case "add" -> {
                // Grant the bypass permission via op—simple approach; server owners can use LuckPerms for production
                if (target != null) {
                    // We can't programmatically grant permissions without a permissions plugin,
                    // so we advise the admin and note the approach.
                    sender.sendMessage(ColorUtil.colorize(prefix + addMsg.replace("{player}", name)));
                    sender.sendMessage(ColorUtil.colorize(prefix + "&7Grant &fanticheat.bypass &7via your permissions plugin to fully whitelist."));
                } else {
                    sender.sendMessage(ColorUtil.colorize(prefix + "&cPlayer must be online to whitelist."));
                }
            }
            case "remove" -> {
                sender.sendMessage(ColorUtil.colorize(prefix + removeMsg.replace("{player}", name)));
                sender.sendMessage(ColorUtil.colorize(prefix + "&7Remove &fanticheat.bypass &7via your permissions plugin."));
            }
            default -> sender.sendMessage(ColorUtil.colorize(prefix + "&cUnknown sub-command. Use add or remove."));
        }
    }

    private void sendHelp(CommandSender sender, String prefix) {
        sender.sendMessage(ColorUtil.colorize("&8--- &cAntiCheat Commands &8---"));
        sender.sendMessage(ColorUtil.colorize("&f/anticheat reload &7- Reload config"));
        sender.sendMessage(ColorUtil.colorize("&f/anticheat status &7- Show plugin status"));
        sender.sendMessage(ColorUtil.colorize("&f/anticheat vl <player> &7- Show player VL"));
        sender.sendMessage(ColorUtil.colorize("&f/anticheat whitelist <add|remove> <player> &7- Manage bypass list"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("anticheat.admin")) return List.of();

        if (args.length == 1) {
            return filter(List.of("reload", "status", "vl", "whitelist"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("whitelist")) return filter(List.of("add", "remove"), args[1]);
            if (args[0].equalsIgnoreCase("vl")) return onlineNames(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) {
            return onlineNames(args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> list, String partial) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(partial.toLowerCase())) result.add(s);
        }
        return result;
    }

    private List<String> onlineNames(String partial) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(partial.toLowerCase())) names.add(p.getName());
        }
        return names;
    }
}
