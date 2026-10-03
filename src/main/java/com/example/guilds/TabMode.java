package com.example.guilds;

import org.bukkit.Material;

/** What is shown in front of a guild member's name in the tab list. */
public enum TabMode {
    NAME("Guild Name", Material.NAME_TAG, "%guild_color%[%guild_name%] &f"),
    RANK("Guild Rank", Material.PAPER, "%guild_color%[%guild_rank%] &f"),
    NAME_RANK("Guild Name + Rank", Material.BOOK, "%guild_color%[%guild_name%|%guild_rank%] &f"),
    NONE("Hidden", Material.BARRIER, "");

    private final String display;
    private final Material icon;
    private final String defaultFormat;

    TabMode(String display, Material icon, String defaultFormat) {
        this.display = display;
        this.icon = icon;
        this.defaultFormat = defaultFormat;
    }

    public String getDisplay() { return display; }
    public Material getIcon() { return icon; }
    public String getDefaultFormat() { return defaultFormat; }
}
