package dev.anticheat.managers;

import dev.anticheat.AntiCheatPlugin;
import dev.anticheat.utils.ColorUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class BanManager {

    private final AntiCheatPlugin plugin;

    public BanManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Bans a player for fly hacking.
     * Duration: 30 days (configurable).
     * Message: "Using a modified client" (configurable).
     */
    public void banPlayer(Player player, String checkName) {
        int durationDays = plugin.getConfig().getInt("ban.duration-days", 30);
        String rawReason = plugin.getConfig().getString(
                "ban.reason",
                "&cYou have been banned.\n\n&fReason: &cUsing a modified client\n\n&7Your ban expires in &f30 days&7."
        );
        String broadcastTemplate = plugin.getConfig().getString(
                "ban.broadcast",
                "&8[&cAntiCheat&8] &f{player} &7has been banned for &cusing a modified client&7."
        );

        String kickMessage = ColorUtil.colorize(rawReason);
        Date expiry = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(durationDays));

        // Apply the ban using Bukkit's BanList
        Bukkit.getBanList(BanList.Type.NAME).addBan(
                player.getName(),
                "Using a modified client (" + checkName + ")",
                expiry,
                "AntiCheat"
        );

        // Kick with the formatted message
        player.kickPlayer(kickMessage);

        // Broadcast to all online players
        String broadcast = ColorUtil.colorize(
                broadcastTemplate.replace("{player}", player.getName())
        );
        Bukkit.broadcastMessage(broadcast);

        // Log to console
        plugin.getLogger().warning("[AntiCheat] BANNED: " + player.getName()
                + " | Check: " + checkName
                + " | Duration: " + durationDays + " days"
                + " | Expires: " + expiry);
    }
}
