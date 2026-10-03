package com.example.guilds;

import java.util.UUID;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion, identifier: guild
 *
 *  %guild_name%          guild name
 *  %guild_name_colored%  guild name with the guild color
 *  %guild_rank%          player's rank in the guild
 *  %guild_color%         guild color (usable as a color prefix, e.g. §a)
 *  %guild_color_code%    just the code character, e.g. a
 *  %guild_prefix%        colored "[GuildName]"
 *  %guild_tab%           the prefix currently used in the tab list
 *  %guild_master%        name of the guild master
 *  %guild_members%       member count
 *  %guild_online%        online member count
 *  %guild_has%           true / false
 */
public class GuildPlaceholders extends PlaceholderExpansion {

    private final GuildPlugin plugin;

    public GuildPlaceholders(GuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "guild"; }
    @Override public String getAuthor() { return "GuildPlugin"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public String onPlaceholderRequest(Player p, String id) {
        if (p == null) return "";

        Guild g = plugin.getGuildManager().getGuild(p.getUniqueId());
        String none = plugin.getConfig().getString("placeholders.no-guild", "");
        String key = id.toLowerCase();

        if (g == null) {
            switch (key) {
                case "has": return "false";
                case "members":
                case "online": return "0";
                default: return none;
            }
        }

        String col = String.valueOf(ChatColor.COLOR_CHAR) + g.getColor();

        switch (key) {
            case "name": return g.getName();
            case "name_colored": return col + g.getName();
            case "rank": return g.getRank(p.getUniqueId());
            case "color": return col;
            case "color_code": return String.valueOf(g.getColor());
            case "prefix": return col + "[" + g.getName() + "]";
            case "tab": return plugin.getTabManager().format(p, g, g.getRank(p.getUniqueId()), g.getTabMode(), false);
            case "master": return g.getMemberName(g.getMaster());
            case "members": return String.valueOf(g.size());
            case "online": {
                int n = 0;
                for (UUID u : g.getMembers()) if (Bukkit.getPlayer(u) != null) n++;
                return String.valueOf(n);
            }
            case "has": return "true";
            default: return null;
        }
    }
}
