package dev.anticheat;

import dev.anticheat.commands.AntiCheatCommand;
import dev.anticheat.listeners.FlyListener;
import dev.anticheat.listeners.PlayerStateListener;
import dev.anticheat.listeners.ReachListener;
import dev.anticheat.managers.BanManager;
import dev.anticheat.managers.ViolationManager;
import dev.anticheat.utils.ColorUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiCheatPlugin extends JavaPlugin {

    private static AntiCheatPlugin instance;
    private BanManager banManager;
    private ViolationManager violationManager;
    private ReachListener reachListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        reloadConfig();

        this.banManager       = new BanManager(this);
        this.violationManager = new ViolationManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new FlyListener(this), this);
        this.reachListener = new ReachListener(this);
        getServer().getPluginManager().registerEvents(reachListener, this);
        getServer().getPluginManager().registerEvents(new PlayerStateListener(this), this);

        // Register commands
        AntiCheatCommand cmd = new AntiCheatCommand(this);
        getCommand("anticheat").setExecutor(cmd);
        getCommand("anticheat").setTabCompleter(cmd);

        getLogger().info(ColorUtil.strip("&a[AntiCheat] Plugin enabled — Bukkit 1.21.1"));
        getLogger().info(ColorUtil.strip("&a[AntiCheat] Fly + Reach detection active | Ban duration: "
                + getConfig().getInt("ban.duration-days") + " days"));
    }

    @Override
    public void onDisable() {
        if (violationManager != null) violationManager.cleanup();
        getLogger().info("[AntiCheat] Plugin disabled.");
    }

    public static AntiCheatPlugin getInstance() { return instance; }
    public BanManager getBanManager()           { return banManager; }
    public ViolationManager getViolationManager() { return violationManager; }
    public ReachListener getReachListener()       { return reachListener; }
}
