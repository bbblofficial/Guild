package com.example.guilds;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/** Small helper for colored messages. Only '&' codes are used (no hex). */
public final class Msg {

    public static final String PREFIX = "&2Guild > ";

    private Msg() {}

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static void send(CommandSender to, String s) {
        to.sendMessage(color(s));
    }

    public static void error(CommandSender to, String s) {
        to.sendMessage(color("&c" + s));
    }

    public static void ok(CommandSender to, String s) {
        to.sendMessage(color("&a" + s));
    }

    public static String line() {
        return "&9&m" + repeat("-", 53);
    }

    public static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    public static String join(String[] a, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < a.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(a[i]);
        }
        return sb.toString();
    }
}
