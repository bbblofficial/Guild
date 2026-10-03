package com.example.guilds;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Keeps stored names fresh and tells guild members when a mate joins or leaves the server. */
public class GuildListener implements Listener {

    private final GuildPlugin plugin;

    public GuildListener(GuildPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Guild guild = plugin.getGuildManager().getGuildOf(player.getUniqueId());
        if (guild == null) {
            return;
        }

        GuildMember member = guild.getMember(player.getUniqueId());
        if (member != null && !member.getName().equals(player.getName())) {
            member.setName(player.getName()); // name change
            plugin.getGuildManager().save();
        }
        notifyOthers(guild, player, "&ajoined.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Guild guild = plugin.getGuildManager().getGuildOf(player.getUniqueId());
        if (guild == null) {
            return;
        }
        notifyOthers(guild, player, "&cleft.");
    }

    private void notifyOthers(Guild guild, Player who, String action) {
        if (!plugin.getConfig().getBoolean("join-leave-messages", true)) {
            return;
        }
        String line = GuildPlugin.color("&2Guild > &e" + who.getName() + " " + action);
        for (Player other : guild.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(who.getUniqueId())) {
                other.sendMessage(line);
            }
        }
    }
}
