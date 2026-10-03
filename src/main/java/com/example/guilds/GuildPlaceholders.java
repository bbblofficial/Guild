package com.example.guilds;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI expansion, registered natively by the plugin (no eCloud download needed).
 *
 * %guild_name%        guild name
 * %guild_tag%         "[GuildName]" (empty when not in a guild)
 * %guild_rank%        the player's rank in the guild
 * %guild_master%      name of the Guild Master
 * %guild_members%     number of members
 * %guild_max_members% configured member limit
 * %guild_online%      number of members currently online
 * %guild_in_guild%    true / false
 * %guild_is_master%   true / false
 */
public class GuildPlaceholders extends PlaceholderExpansion {

    private final GuildPlugin plugin;

    public GuildPlaceholders(GuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "guild";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Keep the expansion registered across /papi reload. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }

        Guild guild = plugin.getGuildManager().getGuildOf(player.getUniqueId());
        String key = params.toLowerCase();

        if (key.equals("in_guild")) {
            return String.valueOf(guild != null);
        }
        if (key.equals("is_master")) {
            return String.valueOf(guild != null && guild.isMaster(player.getUniqueId()));
        }
        if (key.equals("max_members")) {
            return String.valueOf(plugin.getMaxMembers());
        }

        if (guild == null) {
            switch (key) {
                case "name":
                    return plugin.getConfig().getString("placeholders.no-guild-name", "");
                case "rank":
                    return plugin.getConfig().getString("placeholders.no-guild-rank", "");
                case "tag":
                    return plugin.getConfig().getString("placeholders.no-guild-tag", "");
                case "master":
                    return plugin.getConfig().getString("placeholders.no-guild-master", "");
                case "members":
                case "online":
                    return "0";
                default:
                    return null;
            }
        }

        switch (key) {
            case "name":
                return guild.getName();
            case "tag":
                return "[" + guild.getName() + "]";
            case "rank": {
                GuildMember m = guild.getMember(player.getUniqueId());
                return m == null ? "" : m.getRank();
            }
            case "master": {
                GuildMember master = guild.getMasterMember();
                return master == null ? "" : master.getName();
            }
            case "members":
                return String.valueOf(guild.size());
            case "online":
                return String.valueOf(guild.getOnlineCount());
            default:
                return null; // unknown placeholder
        }
    }
}
