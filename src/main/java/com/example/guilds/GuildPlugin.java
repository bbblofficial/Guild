package com.example.guilds;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class GuildPlugin extends JavaPlugin {

    private GuildManager guildManager;
    private TabManager tabManager;
    private GuiManager guiManager;
    private boolean papi;

    /** Players whose normal chat is redirected to guild chat (/g chat with no message). */
    private final Set<UUID> chatToggled = Collections.synchronizedSet(new HashSet<UUID>());

    @Override
    public void onEnable() {
        saveDefaultConfig();

        guildManager = new GuildManager(this);
        guildManager.load();
        tabManager = new TabManager(this);
        guiManager = new GuiManager(this);

        GuildCommand cmd = new GuildCommand(this);
        getCommand("guild").setExecutor(cmd);
        getCommand("guild").setTabCompleter(cmd);
        getCommand("gc").setExecutor(cmd);

        Bukkit.getPluginManager().registerEvents(new GuildListener(this), this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);

        hookPlaceholderApi();

        tabManager.purgeStale();
        for (Player p : Bukkit.getOnlinePlayers()) tabManager.apply(p);

        getLogger().info("GuildPlugin enabled" + (papi ? " (PlaceholderAPI hooked)." : "."));
    }

    @Override
    public void onDisable() {
        if (guildManager != null) guildManager.save();
        if (tabManager != null) tabManager.purgeStale();
    }

    private void hookPlaceholderApi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not found - placeholders disabled.");
            return;
        }
        try {
            papi = new GuildPlaceholders(this).register();
        } catch (Throwable t) {
            papi = false;
            getLogger().warning("Could not hook PlaceholderAPI: " + t.getMessage());
        }
    }

    // --------------------------------------------------------------- getters

    public GuildManager getGuildManager() { return guildManager; }
    public TabManager getTabManager() { return tabManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public boolean hasPapi() { return papi; }
    public Set<UUID> getChatToggled() { return chatToggled; }

    // ------------------------------------------------------------- messaging

    /** Sends an already colored message to every online member of the guild. */
    public void broadcast(Guild g, String coloredMessage) {
        for (UUID u : g.getMembers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(coloredMessage);
        }
    }

    public void broadcastExcept(Guild g, String coloredMessage, UUID except) {
        for (UUID u : g.getMembers()) {
            if (u.equals(except)) continue;
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(coloredMessage);
        }
    }

    public void sendGuildChat(Player sender, String message) {
        Guild g = guildManager.getGuild(sender.getUniqueId());
        if (g == null) return;
        String fmt = getConfig().getString("formats.guild-chat", "&2Guild > &f%player% &e[%rank%]&f: %message%");
        String msg = sender.hasPermission("guild.chat.color") ? Msg.color(message) : message;
        String out = Msg.color(fmt
                .replace("%player%", sender.getName())
                .replace("%rank%", g.getRank(sender.getUniqueId()))
                .replace("%guild%", g.getName()))
                .replace("%message%", msg);
        broadcast(g, out);
        getLogger().info("[GuildChat/" + g.getName() + "] " + ChatColor.stripColor(out));
    }
}
