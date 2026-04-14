package dev.anticheat.listeners;

import dev.anticheat.AntiCheatPlugin;
import dev.anticheat.managers.ViolationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

import java.util.UUID;

public class PlayerStateListener implements Listener {

    private final AntiCheatPlugin plugin;
    private final ViolationManager vm;

    public PlayerStateListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.vm = plugin.getViolationManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        vm.giveJoinGrace(id);
        vm.setPrevY(id, event.getPlayer().getLocation().getY());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        vm.remove(id);
        plugin.getReachListener().removePlayer(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        vm.resetFlyVL(id);
        vm.resetAirTicks(id);
        vm.giveTeleportGrace(id);
        plugin.getReachListener().removePlayer(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        vm.giveTeleportGrace(id);
        vm.resetAirTicks(id);
        if (event.getTo() != null) vm.setPrevY(id, event.getTo().getY());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        vm.giveDamageGrace(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent event) {
        if (event.getVelocity().getY() > 0.3) {
            vm.giveDamageGrace(event.getPlayer().getUniqueId());
        }
    }
}
