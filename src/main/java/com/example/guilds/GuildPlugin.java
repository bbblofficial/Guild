package com.example.guilds;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class GuildPlugin extends JavaPlugin {

    private GuildManager guildManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        guildManager = new GuildManager(this);
        guildManager.load();

        GuildCommand executor = new GuildCommand(this);
        PluginCommand guild = getCommand("guild");
        PluginCommand gc = getCommand("gc");
        if (guild != null) {
            guild.setExecutor(executor);
            guild.setTabCompleter(executor);
        }
        if (gc != null) {
            gc.setExecutor(executor);
        }

        getServer().getPluginManager().registerEvents(new GuildListener(this), this);

        // Expire stale invites / join requests every 30 seconds
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                guildManager.cleanupExpired();
            }
        }, 600L, 600L);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            hookPlaceholderAPI();
        } else {
            getLogger().info("PlaceholderAPI not found - %guild_...% placeholders are disabled.");
        }

        getLogger().info("Guilds enabled.");
    }

    /** Kept in its own method so PlaceholderAPI classes are only touched when the plugin exists. */
    private void hookPlaceholderAPI() {
        if (new GuildPlaceholders(this).register()) {
            getLogger().info("Registered PlaceholderAPI expansion (%guild_...%).");
        } else {
            getLogger().warning("Could not register the PlaceholderAPI expansion.");
        }
    }

    @Override
    public void onDisable() {
        if (guildManager != null) {
            guildManager.saveNow();
        }
    }

    public GuildManager getGuildManager() {
        return guildManager;
    }

    public int getMaxMembers() {
        return Math.max(1, getConfig().getInt("max-members", 50));
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
