package dev.anticheat.managers;

import dev.anticheat.AntiCheatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ViolationManager {

    private final AntiCheatPlugin plugin;

    /** Fly violation level per player */
    private final Map<UUID, Integer> flyVL = new ConcurrentHashMap<>();

    /** Grace ticks remaining per player for various situations */
    private final Map<UUID, Integer> jumpGrace       = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> damageGrace     = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> teleportGrace   = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> joinGrace        = new ConcurrentHashMap<>();

    /** Previous Y position for motion tracking */
    private final Map<UUID, Double> prevY = new ConcurrentHashMap<>();

    /** Consecutive airborne ticks (how long player has been off ground) */
    private final Map<UUID, Integer> airticks = new ConcurrentHashMap<>();

    private final BukkitTask decayTask;

    public ViolationManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;

        int decayInterval = plugin.getConfig().getInt("settings.vl-decay-ticks", 40);

        // Periodically decay VL so false positives don't stack forever
        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::decayAll, decayInterval, decayInterval);
    }

    // ── Grace tick helpers ──────────────────────────────────────────────────

    public void giveJumpGrace(UUID id) {
        jumpGrace.put(id, plugin.getConfig().getInt("settings.jump-grace-ticks", 6));
    }

    public void giveDamageGrace(UUID id) {
        damageGrace.put(id, plugin.getConfig().getInt("settings.damage-grace-ticks", 10));
    }

    public void giveTeleportGrace(UUID id) {
        teleportGrace.put(id, plugin.getConfig().getInt("settings.teleport-grace-ticks", 20));
    }

    public void giveJoinGrace(UUID id) {
        joinGrace.put(id, plugin.getConfig().getInt("settings.join-grace-ticks", 60));
    }

    /** Decrement all grace timers by 1 tick (called from FlyListener each move event). */
    public void tickGrace(UUID id) {
        tickDown(jumpGrace, id);
        tickDown(damageGrace, id);
        tickDown(teleportGrace, id);
        tickDown(joinGrace, id);
    }

    private void tickDown(Map<UUID, Integer> map, UUID id) {
        Integer v = map.get(id);
        if (v != null) {
            if (v <= 1) map.remove(id);
            else map.put(id, v - 1);
        }
    }

    public boolean hasJumpGrace(UUID id)     { return jumpGrace.containsKey(id); }
    public boolean hasDamageGrace(UUID id)   { return damageGrace.containsKey(id); }
    public boolean hasTeleportGrace(UUID id) { return teleportGrace.containsKey(id); }
    public boolean hasJoinGrace(UUID id)     { return joinGrace.containsKey(id); }

    // ── VL helpers ─────────────────────────────────────────────────────────

    public int getFlyVL(UUID id) {
        return flyVL.getOrDefault(id, 0);
    }

    public void incrementFlyVL(UUID id, int amount) {
        flyVL.put(id, getFlyVL(id) + amount);
    }

    public void resetFlyVL(UUID id) {
        flyVL.remove(id);
    }

    // ── Air-tick tracking ──────────────────────────────────────────────────

    public int getAirTicks(UUID id) {
        return airticks.getOrDefault(id, 0);
    }

    public void incrementAirTicks(UUID id) {
        airticks.put(id, getAirTicks(id) + 1);
    }

    public void resetAirTicks(UUID id) {
        airticks.remove(id);
    }

    // ── Y tracking ─────────────────────────────────────────────────────────

    public Double getPrevY(UUID id) {
        return prevY.get(id);
    }

    public void setPrevY(UUID id, double y) {
        prevY.put(id, y);
    }

    // ── Cleanup ────────────────────────────────────────────────────────────

    public void remove(UUID id) {
        flyVL.remove(id);
        jumpGrace.remove(id);
        damageGrace.remove(id);
        teleportGrace.remove(id);
        joinGrace.remove(id);
        prevY.remove(id);
        airticks.remove(id);
    }

    public void cleanup() {
        if (decayTask != null) decayTask.cancel();
        flyVL.clear();
        prevY.clear();
        airticks.clear();
    }

    // ── Decay logic ────────────────────────────────────────────────────────

    private void decayAll() {
        for (UUID id : flyVL.keySet()) {
            int current = flyVL.getOrDefault(id, 0);
            if (current > 0) flyVL.put(id, current - 1);
            else flyVL.remove(id);
        }
    }
}
