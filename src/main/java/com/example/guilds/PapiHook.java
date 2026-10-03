package com.example.guilds;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** Isolated so the plugin still loads when PlaceholderAPI is not installed. */
final class PapiHook {
    private PapiHook() {}

    static String apply(Player p, String text) {
        return PlaceholderAPI.setPlaceholders(p, text);
    }
}
