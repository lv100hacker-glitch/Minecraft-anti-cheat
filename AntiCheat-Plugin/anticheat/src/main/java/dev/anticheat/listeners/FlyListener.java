package dev.anticheat.listeners;

import dev.anticheat.AntiCheatPlugin;
import dev.anticheat.managers.BanManager;
import dev.anticheat.managers.ViolationManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlyListener implements Listener {

    private final AntiCheatPlugin plugin;
    private final ViolationManager vm;
    private final BanManager bm;

    // Per-player motion tracking
    private final Map<UUID, Double>  prevDeltaY   = new HashMap<>();
    private final Map<UUID, Integer> hoverTicks   = new HashMap<>();
    private final Map<UUID, Boolean> wasOnGround  = new HashMap<>();
    private final Map<UUID, Integer> upTicks      = new HashMap<>();

    // Vanilla physics constants
    private static final double GRAVITY          = 0.08;
    private static final double DRAG             = 0.98;
    private static final double MAX_JUMP_VEL     = 0.42;
    private static final double HOVER_THRESHOLD  = 0.015;
    private static final int    HOVER_TICKS_MAX  = 4;
    private static final int    MAX_AIR_TICKS    = 14; // vanilla jump apex ~12 ticks

    public FlyListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.vm = plugin.getViolationManager();
        this.bm = plugin.getBanManager();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIMARY: Movement-based fly detection
    // ─────────────────────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID   id     = player.getUniqueId();

        vm.tickGrace(id);

        if (isExempt(player)) {
            resetState(id, player.getLocation().getY());
            return;
        }

        Location to = event.getTo();
        if (to == null) return;

        double prevY  = vm.getPrevY(id) != null ? vm.getPrevY(id) : to.getY();
        double deltaY = to.getY() - prevY;
        vm.setPrevY(id, to.getY());

        boolean onGround    = isOnGround(player, to);
        boolean inLiquid    = isInOrNearLiquid(player);
        boolean onClimbable = isOnClimbable(player);
        boolean isGliding   = player.isGliding();
        boolean hasLev      = player.hasPotionEffect(PotionEffectType.LEVITATION);
        boolean hadGround   = wasOnGround.getOrDefault(id, true);
        wasOnGround.put(id, onGround);

        // Detect jump start → give grace
        if (hadGround && !onGround && deltaY > 0.1) {
            vm.giveJumpGrace(id);
            upTicks.put(id, 0);
        }

        if (onGround || inLiquid || onClimbable || isGliding || hasLev) {
            resetState(id, to.getY());
            return;
        }

        vm.incrementAirTicks(id);
        int airTicks = vm.getAirTicks(id);

        if (vm.hasJumpGrace(id) || vm.hasDamageGrace(id)
                || vm.hasTeleportGrace(id) || vm.hasJoinGrace(id)) {
            prevDeltaY.put(id, deltaY);
            return;
        }

        // ── CHECK 1: Upward velocity exceeds vanilla maximum ───────────────
        double maxUpVel = plugin.getConfig().getDouble("settings.max-upward-velocity", MAX_JUMP_VEL);
        if (player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
            int amp = player.getPotionEffect(PotionEffectType.JUMP_BOOST).getAmplifier() + 1;
            maxUpVel += amp * 0.1;
        }
        if (deltaY > maxUpVel + 0.01) {
            flag(player, id, "Fly[UpVelocity] dY=" + fmt(deltaY) + " max=" + fmt(maxUpVel), 1);
        }

        // ── CHECK 2: Gravity violation — not falling fast enough ───────────
        // Each tick: nextDY = (prevDY * drag) - gravity
        Double prevDY = prevDeltaY.get(id);
        if (prevDY != null && airTicks > 2) {
            double expectedDY = (prevDY * DRAG) - GRAVITY;
            if (deltaY > expectedDY + 0.08 && deltaY > 0) {
                flag(player, id, "Fly[GravityViolation] dY=" + fmt(deltaY) + " expected≤" + fmt(expectedDY + 0.08), 1);
            }
        }
        prevDeltaY.put(id, deltaY);

        // ── CHECK 3: Hover — nearly zero Y movement while airborne ─────────
        if (Math.abs(deltaY) < HOVER_THRESHOLD) {
            int ht = hoverTicks.getOrDefault(id, 0) + 1;
            hoverTicks.put(id, ht);
            if (ht > HOVER_TICKS_MAX) {
                flag(player, id, "Fly[Hover] hoverTicks=" + ht, 1);
            }
        } else {
            hoverTicks.put(id, 0);
        }

        // ── CHECK 4: Moving upward past apex — physically impossible ────────
        if (airTicks > MAX_AIR_TICKS && deltaY > 0.04) {
            flag(player, id, "Fly[UpPostApex] airTick=" + airTicks + " dY=" + fmt(deltaY), 1);
        }

        // ── CHECK 5: Consecutive upward ticks while airborne ───────────────
        if (deltaY > 0.05) {
            int ut = upTicks.getOrDefault(id, 0) + 1;
            upTicks.put(id, ut);
            if (ut >= 3) {
                flag(player, id, "Fly[ConsecUpTicks] upTicks=" + ut, 1);
            }
        } else {
            upTicks.put(id, 0);
        }

        // ── CHECK 6: Static hover — long airtime with no Y motion at all ───
        if (airTicks > 30 && Math.abs(deltaY) < 0.001) {
            flag(player, id, "Fly[StaticHover] airTicks=" + airTicks, 1);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECONDARY: Client-side illegal flight toggle
    // ─────────────────────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        UUID   id     = player.getUniqueId();
        if (isExempt(player)) return;

        if (event.isFlying()) {
            event.setCancelled(true);
            debugLog(player, "Fly[ToggleFlight] — cancelling & flagging +2 VL");
            flag(player, id, "Fly[ToggleFlight]", 2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void flag(Player player, UUID id, String checkName, int vlAmount) {
        vm.incrementFlyVL(id, vlAmount);
        int vl        = vm.getFlyVL(id);
        int threshold = plugin.getConfig().getInt("settings.fly-vl-threshold", 3);
        debugLog(player, checkName + " VL=" + vl + "/" + threshold);
        if (vl >= threshold) {
            vm.remove(id);
            resetState(id, 0);
            bm.banPlayer(player, checkName);
        }
    }

    private void resetState(UUID id, double y) {
        vm.resetAirTicks(id);
        vm.resetFlyVL(id);
        vm.setPrevY(id, y);
        hoverTicks.remove(id);
        prevDeltaY.remove(id);
        upTicks.remove(id);
    }

    private void debugLog(Player player, String msg) {
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[DEBUG] " + player.getName() + " » " + msg);
        }
    }

    private boolean isExempt(Player player) {
        GameMode gm = player.getGameMode();
        return player.hasPermission("anticheat.bypass")
                || gm == GameMode.CREATIVE
                || gm == GameMode.SPECTATOR
                || player.getAllowFlight()
                || player.isFlying()
                || player.isDead()
                || vm.hasJoinGrace(player.getUniqueId());
    }

    /** Multi-point ground check to handle fences, slabs, and edge cases. */
    private boolean isOnGround(Player player, Location loc) {
        if (player.isOnGround()) return true;
        Block below = loc.clone().subtract(0, 0.1, 0).getBlock();
        if (below.getType().isSolid()) return true;
        Block lower = loc.clone().subtract(0, 0.6, 0).getBlock();
        return lower.getType().isSolid();
    }

    private boolean isInOrNearLiquid(Player player) {
        Location loc = player.getLocation();
        for (int dy = -1; dy <= 1; dy++) {
            Material m = loc.clone().add(0, dy, 0).getBlock().getType();
            if (m == Material.WATER || m == Material.LAVA) return true;
        }
        return false;
    }

    private boolean isOnClimbable(Player player) {
        Material m = player.getLocation().getBlock().getType();
        return switch (m) {
            case LADDER, VINE, SCAFFOLDING,
                 TWISTING_VINES, TWISTING_VINES_PLANT,
                 WEEPING_VINES, WEEPING_VINES_PLANT,
                 CAVE_VINES, CAVE_VINES_PLANT,
                 KELP_PLANT -> true;
            default -> false;
        };
    }

    private String fmt(double d) { return String.format("%.4f", d); }
}
