package dev.anticheat.listeners;

import dev.anticheat.AntiCheatPlugin;
import dev.anticheat.managers.BanManager;
import dev.anticheat.managers.ViolationManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReachListener implements Listener {

    private final AntiCheatPlugin plugin;
    private final ViolationManager vm;
    private final BanManager bm;

    // Per-player reach violation tracking (separate from fly VL)
    private final Map<UUID, Integer> reachVL        = new HashMap<>();
    private final Map<UUID, Long>    lastFlagTime   = new HashMap<>();

    // Vanilla creative reach = 5.0, survival = 3.0 (Bukkit measures eye-to-hitbox)
    // We allow a small buffer for ping/lag compensation
    private static final double MAX_SURVIVAL_REACH  = 3.2;   // 3.0 + 0.2 lag buffer
    private static final double MAX_CREATIVE_REACH  = 5.2;
    private static final double INSTANT_BAN_REACH   = 5.5;   // blatant reach — ban immediately regardless of VL
    private static final long   FLAG_COOLDOWN_MS    = 500;   // don't flag the same player twice within 500ms

    public ReachListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.vm = plugin.getViolationManager();
        this.bm = plugin.getBanManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        // Only care about player-dealt damage
        if (!(event.getDamager() instanceof Player attacker)) return;
        Entity victim = event.getEntity();
        UUID id = attacker.getUniqueId();

        // Exempt checks
        if (isExempt(attacker)) return;
        if (!(victim instanceof LivingEntity)) return;

        // Rate-limit flags per player to avoid spam from lag bursts
        long now = System.currentTimeMillis();
        Long lastFlag = lastFlagTime.get(id);
        if (lastFlag != null && now - lastFlag < FLAG_COOLDOWN_MS) return;

        // ── Distance calculation ───────────────────────────────────────────
        // Measure from attacker's eye position to the nearest point of the
        // victim's bounding box (most accurate method available in Bukkit).
        double distance = getEyeToHitboxDistance(attacker, victim);

        double maxReach = attacker.getGameMode() == GameMode.CREATIVE
                ? MAX_CREATIVE_REACH
                : MAX_SURVIVAL_REACH;

        // Also factor in a configurable extra buffer from config
        double configBuffer = plugin.getConfig().getDouble("settings.reach-lag-buffer", 0.0);
        maxReach += configBuffer;

        if (distance <= maxReach) return; // legitimate hit

        lastFlagTime.put(id, now);

        double excess = distance - (maxReach - configBuffer); // excess over vanilla (no buffer)

        // ── Instant ban for blatant reach (e.g. kill-aura at 6+ blocks) ───
        if (distance > INSTANT_BAN_REACH) {
            debugLog(attacker, "Reach[Blatant] dist=" + fmt(distance) + " (instant ban)");
            reachVL.remove(id);
            bm.banPlayer(attacker, "Reach[Blatant] dist=" + fmt(distance));
            return;
        }

        // ── VL-based flagging for borderline reach ─────────────────────────
        int vlIncrease = excess > 1.0 ? 2 : 1;
        int vl = reachVL.getOrDefault(id, 0) + vlIncrease;
        reachVL.put(id, vl);

        int threshold = plugin.getConfig().getInt("settings.reach-vl-threshold", 3);
        debugLog(attacker, "Reach[Flag] dist=" + fmt(distance) + " max=" + fmt(maxReach)
                + " excess=" + fmt(excess) + " VL=" + vl + "/" + threshold);

        if (vl >= threshold) {
            reachVL.remove(id);
            lastFlagTime.remove(id);
            bm.banPlayer(attacker, "Reach dist=" + fmt(distance));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accurate distance: attacker eye → victim bounding box nearest point
    // ─────────────────────────────────────────────────────────────────────────
    private double getEyeToHitboxDistance(Player attacker, Entity victim) {
        Location eye = attacker.getEyeLocation();

        // Get victim bounding box center and half-extents
        Location vLoc   = victim.getLocation();
        double   height = victim.getHeight();
        double   width  = victim.getWidth();
        double   halfW  = width / 2.0;

        // Bounding box min/max
        double minX = vLoc.getX() - halfW;
        double maxX = vLoc.getX() + halfW;
        double minY = vLoc.getY();
        double maxY = vLoc.getY() + height;
        double minZ = vLoc.getZ() - halfW;
        double maxZ = vLoc.getZ() + halfW;

        // Clamp eye position to bounding box to find nearest point
        double nearX = clamp(eye.getX(), minX, maxX);
        double nearY = clamp(eye.getY(), minY, maxY);
        double nearZ = clamp(eye.getZ(), minZ, maxZ);

        // Distance from eye to nearest point on hitbox
        double dx = eye.getX() - nearX;
        double dy = eye.getY() - nearY;
        double dz = eye.getZ() - nearZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    public void removePlayer(UUID id) {
        reachVL.remove(id);
        lastFlagTime.remove(id);
    }

    private boolean isExempt(Player player) {
        return player.hasPermission("anticheat.bypass")
                || player.getGameMode() == GameMode.SPECTATOR
                || player.isDead()
                || vm.hasJoinGrace(player.getUniqueId());
    }

    private void debugLog(Player player, String msg) {
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[DEBUG] " + player.getName() + " » " + msg);
        }
    }

    private String fmt(double d) { return String.format("%.3f", d); }
}
