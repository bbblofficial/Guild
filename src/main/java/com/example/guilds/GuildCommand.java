package com.example.guilds;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Handles /guild (alias /g) and /gc. */
public class GuildCommand implements CommandExecutor, TabCompleter {

    private static final Pattern GUILD_NAME = Pattern.compile("^[A-Za-z0-9]{3,16}$");
    private static final Pattern RANK_NAME = Pattern.compile("^[A-Za-z0-9]{1,16}$");
    private static final String LINE = "&9&m-----------------------------------------------------";
    private static final String DOT = "●";

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "create", "disband", "invite", "join", "chat", "createrank", "deleterank",
            "promote", "demote", "leave", "kick", "list", "info", "transfer");

    private final GuildPlugin plugin;
    private final GuildManager gm;
    private final Map<UUID, Long> pendingDisband = new HashMap<UUID, Long>();

    public GuildCommand(GuildPlugin plugin) {
        this.plugin = plugin;
        this.gm = plugin.getGuildManager();
    }

    // ===================================================================== entry

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // /gc <message>
        if (cmd.getName().equalsIgnoreCase("gc")) {
            if (!(sender instanceof Player)) {
                err(sender, "Only players can use guild chat.");
                return true;
            }
            Player p = (Player) sender;
            if (!p.hasPermission("guild.use")) {
                err(p, "You don't have permission to do that.");
                return true;
            }
            chat(p, args, 0);
            return true;
        }

        // console-friendly admin command
        if (args.length > 0 && args[0].equalsIgnoreCase("admindisband")) {
            adminDisband(sender, args);
            return true;
        }

        if (!(sender instanceof Player)) {
            err(sender, "Only players can use guild commands.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("guild.use")) {
            err(p, "You don't have permission to do that.");
            return true;
        }

        if (args.length == 0) {
            help(p);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
            case "?":
                help(p);
                break;
            case "create":
                create(p, args);
                break;
            case "disband":
                disband(p, args);
                break;
            case "invite":
                invite(p, args);
                break;
            case "join":
            case "accept":
                join(p, args);
                break;
            case "chat":
                chat(p, args, 1);
                break;
            case "createrank":
                createRank(p, args);
                break;
            case "deleterank":
                deleteRank(p, args);
                break;
            case "promote":
                changeRank(p, args, true);
                break;
            case "demote":
                changeRank(p, args, false);
                break;
            case "leave":
                leave(p);
                break;
            case "kick":
                kick(p, args);
                break;
            case "list":
                list(p);
                break;
            case "info":
                info(p, args);
                break;
            case "transfer":
                transfer(p, args);
                break;
            default:
                err(p, "Unknown sub-command. Type /g help for a list of commands.");
                break;
        }
        return true;
    }

    // ==================================================================== create

    private void create(Player p, String[] a) {
        if (!p.hasPermission("guild.create")) {
            err(p, "You don't have permission to create a guild.");
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /g create <name>");
            return;
        }
        if (gm.getGuildOf(p.getUniqueId()) != null) {
            err(p, "You are already in a guild! Leave it first with /g leave.");
            return;
        }
        String name = a[1];
        if (!GUILD_NAME.matcher(name).matches()) {
            err(p, "Guild names must be 3-16 characters long and only contain letters and numbers.");
            return;
        }
        if (gm.getGuild(name) != null) {
            err(p, "A guild named '" + name + "' already exists.");
            return;
        }
        Guild g = gm.createGuild(name, p);
        send(p, LINE);
        send(p, "&aYou created the guild &b" + g.getName() + "&a!");
        send(p, "&7Invite players with &e/g invite <player>&7.");
        send(p, LINE);
    }

    // =================================================================== disband

    private void disband(Player p, String[] a) {
        Guild g = requireGuild(p);
        if (g == null || !requireMaster(p, g)) {
            return;
        }

        if (plugin.getConfig().getBoolean("require-disband-confirmation", true)) {
            boolean confirm = a.length >= 2 && a[1].equalsIgnoreCase("confirm");
            Long until = pendingDisband.get(p.getUniqueId());
            long now = System.currentTimeMillis();
            if (!confirm || until == null || until < now) {
                pendingDisband.put(p.getUniqueId(), now + 15000L);
                send(p, LINE);
                send(p, "&cThis will permanently disband &b" + g.getName() + "&c and remove all " + g.size() + " member(s).");
                send(p, "&eType &6/g disband confirm &ewithin 15 seconds to continue.");
                send(p, LINE);
                return;
            }
            pendingDisband.remove(p.getUniqueId());
        }

        List<Player> online = g.getOnlinePlayers();
        String name = g.getName();
        gm.disband(g);
        for (Player member : online) {
            if (member.getUniqueId().equals(p.getUniqueId())) {
                send(member, "&aYou disbanded the guild &b" + name + "&a.");
            } else {
                send(member, "&2Guild > &cThe guild &b" + name + "&c was disbanded by &e" + p.getName() + "&c.");
            }
        }
    }

    private void adminDisband(CommandSender s, String[] a) {
        if (!s.hasPermission("guild.admin")) {
            err(s, "You don't have permission to do that.");
            return;
        }
        if (a.length < 2) {
            err(s, "Usage: /g admindisband <guild>");
            return;
        }
        Guild g = gm.getGuild(a[1]);
        if (g == null) {
            err(s, "No guild named '" + a[1] + "' exists.");
            return;
        }
        List<Player> online = g.getOnlinePlayers();
        String name = g.getName();
        gm.disband(g);
        for (Player member : online) {
            send(member, "&2Guild > &cYour guild &b" + name + "&c was disbanded by an administrator.");
        }
        send(s, "&aDisbanded the guild &b" + name + "&a.");
    }

    // ==================================================================== invite

    private void invite(Player p, String[] a) {
        Guild g = requireGuild(p);
        if (g == null) {
            return;
        }
        if (!isStaff(g, p.getUniqueId())) {
            err(p, "Only the Guild Master and Officers can invite players.");
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /g invite <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            err(p, "Player '" + a[1] + "' is not online.");
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            err(p, "You can't invite yourself.");
            return;
        }
        if (gm.getGuildOf(target.getUniqueId()) != null) {
            err(p, target.getName() + " is already in a guild.");
            return;
        }
        if (g.size() >= plugin.getMaxMembers()) {
            err(p, "Your guild is full (" + plugin.getMaxMembers() + " members).");
            return;
        }

        // The target asked to join earlier -> inviting them accepts the request.
        if (gm.consumeRequest(g, target.getUniqueId())) {
            gm.addMember(g, target);
            send(target, "&aYour request was accepted! You joined the guild &b" + g.getName() + "&a.");
            broadcast(g, "&e" + target.getName() + " &ajoined the guild! &7(accepted by " + p.getName() + ")");
            return;
        }

        if (gm.hasInvite(target.getUniqueId(), g)) {
            err(p, target.getName() + " already has a pending invite to your guild.");
            return;
        }

        long ttl = plugin.getConfig().getLong("invite-expire-seconds", 60L);
        gm.addInvite(target.getUniqueId(), g, ttl * 1000L);

        send(p, "&aYou invited &e" + target.getName() + " &ato your guild. &7(expires in " + ttl + "s)");
        broadcastExcept(g, p.getUniqueId(), "&e" + p.getName() + " &ainvited &e" + target.getName() + " &ato the guild.");

        send(target, LINE);
        send(target, "&e" + p.getName() + " &ainvited you to join the guild &b" + g.getName() + "&a!");
        TextComponent click = new TextComponent(GuildPlugin.color("&a&l[CLICK HERE TO JOIN]"));
        click.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/guild join " + g.getName()));
        click.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[]{new TextComponent(GuildPlugin.color("&7/g join " + g.getName()))}));
        target.spigot().sendMessage(click);
        send(target, "&7You have " + ttl + " seconds to accept.");
        send(target, LINE);
    }

    // ====================================================================== join

    private void join(Player p, String[] a) {
        if (a.length < 2) {
            err(p, "Usage: /g join <guild name>");
            List<String> invited = gm.getInvitedGuildNames(p.getUniqueId());
            if (!invited.isEmpty()) {
                send(p, "&7Pending invites: &b" + join(invited, "&7, &b"));
            }
            return;
        }
        if (gm.getGuildOf(p.getUniqueId()) != null) {
            err(p, "You are already in a guild! Leave it first with /g leave.");
            return;
        }
        Guild g = gm.getGuild(a[1]);
        if (g == null) {
            err(p, "No guild named '" + a[1] + "' exists.");
            return;
        }
        if (g.size() >= plugin.getMaxMembers()) {
            err(p, "That guild is full.");
            return;
        }

        // 1) valid invite -> join right away
        if (gm.consumeInvite(p.getUniqueId(), g)) {
            gm.addMember(g, p);
            send(p, LINE);
            send(p, "&aYou joined the guild &b" + g.getName() + "&a!");
            send(p, LINE);
            broadcastExcept(g, p.getUniqueId(), "&e" + p.getName() + " &ajoined the guild!");
            return;
        }

        // 2) no invite -> send a join request to the guild staff
        if (gm.hasRequest(g, p.getUniqueId())) {
            err(p, "You already requested to join " + g.getName() + ". Please wait for an Officer to accept.");
            return;
        }
        long ttl = plugin.getConfig().getLong("request-expire-seconds", 300L);
        gm.addRequest(g, p.getUniqueId(), ttl * 1000L);
        send(p, "&aYou requested to join &b" + g.getName() + "&a. &7(expires in " + (ttl / 60L > 0 ? (ttl / 60L) + "m" : ttl + "s") + ")");

        boolean notified = false;
        for (Player member : g.getOnlinePlayers()) {
            if (isStaff(g, member.getUniqueId())) {
                notified = true;
                send(member, LINE);
                send(member, "&e" + p.getName() + " &arequested to join the guild!");
                TextComponent click = new TextComponent(GuildPlugin.color("&a&l[CLICK TO ACCEPT]"));
                click.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/guild invite " + p.getName()));
                click.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new BaseComponent[]{new TextComponent(GuildPlugin.color("&7/g invite " + p.getName()))}));
                member.spigot().sendMessage(click);
                send(member, LINE);
            }
        }
        if (!notified) {
            send(p, "&7No Guild Master or Officer is online right now - the request stays open until it expires.");
        }
    }

    // ====================================================================== chat

    private void chat(Player p, String[] a, int start) {
        Guild g = requireGuild(p);
        if (g == null) {
            return;
        }
        if (a.length <= start) {
            err(p, "Usage: /g chat <message>  or  /gc <message>");
            return;
        }
        String message = join(Arrays.asList(a).subList(start, a.length), " ");
        if (p.hasPermission("guild.chat.color")) {
            message = GuildPlugin.color(message);
        }
        GuildMember me = g.getMember(p.getUniqueId());
        String rank = me == null ? Guild.DEFAULT_RANK : me.getRank();

        String format = plugin.getConfig().getString("guild-chat-format", "&2Guild > &b{player} &e[{rank}]&f: {message}");
        String line = GuildPlugin.color(format)
                .replace("{player}", p.getName())
                .replace("{rank}", rank)
                .replace("{guild}", g.getName())
                .replace("{message}", message);

        for (Player member : g.getOnlinePlayers()) {
            member.sendMessage(line);
        }
        plugin.getLogger().info("[GuildChat:" + g.getName() + "] " + p.getName() + ": " + ChatColor.stripColor(message));
    }

    // ================================================================= createrank

    private void createRank(Player p, String[] a) {
        Guild g = requireGuild(p);
        if (g == null || !requireMaster(p, g)) {
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /g createrank <rankName>");
            return;
        }
        String name = a[1];
        if (!RANK_NAME.matcher(name).matches()) {
            err(p, "Rank names must be 1-16 characters and only contain letters and numbers.");
            return;
        }
        if (name.equalsIgnoreCase("master") || name.equalsIgnoreCase("guildmaster")) {
            err(p, "That rank name is reserved.");
            return;
        }
        int max = plugin.getConfig().getInt("max-custom-ranks", 8);
        if (g.getCustomRankCount() >= max) {
            err(p, "Your guild can only have " + max + " custom ranks.");
            return;
        }
        if (!g.addRank(name)) {
            err(p, "A rank named '" + g.canonicalRank(name) + "' already exists.");
            return;
        }
        gm.save();
        send(p, "&aCreated the rank &e" + name + "&a. It sits just above &e" + Guild.DEFAULT_RANK + "&a.");
        send(p, "&7Use &e/g promote <player>&7 to give it to someone.");
    }

    // ================================================================= deleterank

    private void deleteRank(Player p, String[] a) {
        Guild g = requireGuild(p);
        if (g == null || !requireMaster(p, g)) {
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /g deleterank <rankName>");
            return;
        }
        String stored = g.canonicalRank(a[1]);
        if (stored == null) {
            err(p, "Your guild has no rank called '" + a[1] + "'.");
            return;
        }
        if (!g.isCustomRank(stored)) {
            err(p, "The default ranks (Guild Master, " + Guild.OFFICER_RANK + ", " + Guild.DEFAULT_RANK + ") can't be deleted.");
            return;
        }
        int moved = g.removeRank(stored);
        gm.save();
        send(p, "&aDeleted the rank &e" + stored + "&a." + (moved > 0 ? " &7" + moved + " member(s) were moved to " + Guild.DEFAULT_RANK + "." : ""));
    }

    // ============================================================ promote/demote

    private void changeRank(Player p, String[] a, boolean promote) {
        String verb = promote ? "promote" : "demote";
        Guild g = requireGuild(p);
        if (g == null) {
            return;
        }
        int myLevel = g.getLevel(p.getUniqueId());
        if (myLevel > 1) {
            err(p, "Only the Guild Master and Officers can " + verb + " members.");
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /g " + verb + " <player>");
            return;
        }
        GuildMember target = g.getMemberByName(a[1]);
        if (target == null) {
            err(p, "'" + a[1] + "' is not in your guild.");
            return;
        }
        if (target.getUuid().equals(p.getUniqueId())) {
            err(p, "You can't " + verb + " yourself.");
            return;
        }
        if (g.isMaster(target.getUuid())) {
            err(p, "The Guild Master can't be " + verb + "d. Use /g transfer to hand over leadership.");
            return;
        }
        int targetLevel = g.getLevel(target.getRank());
        if (myLevel >= targetLevel) {
            err(p, "You can only " + verb + " members ranked below you.");
            return;
        }

        String oldRank = target.getRank();
        String newRank = promote ? g.higherRank(oldRank) : g.lowerRank(oldRank);
        if (newRank == null) {
            if (promote) {
                err(p, target.getName() + " is already at the highest rank that can be given. Use /g transfer to make someone Guild Master.");
            } else {
                err(p, target.getName() + " is already at the lowest rank. Use /g kick to remove them.");
            }
            return;
        }
        if (promote && myLevel >= g.getLevel(newRank)) {
            err(p, "You can't promote someone to your own rank or higher.");
            return;
        }

        target.setRank(newRank);
        gm.save();

        broadcast(g, "&e" + p.getName() + " &a" + (promote ? "promoted" : "demoted") + " &e" + target.getName()
                + " &afrom &e" + oldRank + " &ato &e" + newRank + "&a.");
    }

    // ===================================================================== leave

    private void leave(Player p) {
        Guild g = requireGuild(p);
        if (g == null) {
            return;
        }
        if (g.isMaster(p.getUniqueId())) {
            err(p, "You are the Guild Master! Hand over leadership with /g transfer <player> or use /g disband.");
            return;
        }
        gm.removeMember(g, p.getUniqueId());
        send(p, "&aYou left the guild &b" + g.getName() + "&a.");
        broadcast(g, "&e" + p.getName() + " &cleft the guild.");
    }

    // ====================================================================== kick

    private void kick(Player p, String[] a) {
        Guild g = requireGuild(p);
        if (g == null) {
            return;
        }
        int myLevel = g.getLevel(p.getUniqueId());
        if (myLevel > 1) {
            err(p, "Only the Guild Master and Officers can kick members.");
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /g kick <player> [reason]");
            return;
        }
        GuildMember target = g.getMemberByName(a[1]);
        if (target == null) {
            err(p, "'" + a[1] + "' is not in your guild.");
            return;
        }
        if (target.getUuid().equals(p.getUniqueId())) {
            err(p, "You can't kick yourself. Use /g leave.");
            return;
        }
        if (myLevel >= g.getLevel(target.getRank())) {
            err(p, "You can only kick members ranked below you.");
            return;
        }
        String reason = a.length > 2 ? join(Arrays.asList(a).subList(2, a.length), " ") : null;

        gm.removeMember(g, target.getUuid());
        Player online = Bukkit.getPlayer(target.getUuid());
        if (online != null) {
            send(online, "&cYou were kicked from the guild &b" + g.getName() + " &cby &e" + p.getName() + "&c."
                    + (reason != null ? " &7Reason: " + reason : ""));
        }
        broadcast(g, "&e" + target.getName() + " &cwas kicked by &e" + p.getName() + "&c."
                + (reason != null ? " &7Reason: " + reason : ""));
    }

    // ================================================================== transfer

    private void transfer(Player p, String[] a) {
        Guild g = requireGuild(p);
        if (g == null || !requireMaster(p, g)) {
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /g transfer <player>");
            return;
        }
        GuildMember target = g.getMemberByName(a[1]);
        if (target == null) {
            err(p, "'" + a[1] + "' is not in your guild.");
            return;
        }
        if (target.getUuid().equals(p.getUniqueId())) {
            err(p, "You already are the Guild Master.");
            return;
        }
        GuildMember me = g.getMember(p.getUniqueId());
        me.setRank(Guild.OFFICER_RANK);
        target.setRank(Guild.MASTER_RANK);
        g.setMaster(target.getUuid());
        gm.save();
        broadcast(g, "&e" + p.getName() + " &atransferred the guild to &e" + target.getName() + "&a, the new Guild Master!");
    }

    // ====================================================================== list

    private void list(Player p) {
        Guild g = requireGuild(p);
        if (g == null) {
            return;
        }
        send(p, LINE);
        List<String> order = new ArrayList<String>();
        order.add(Guild.MASTER_RANK);
        order.addAll(g.getRanks());

        int online = 0;
        for (String rank : order) {
            List<GuildMember> inRank = new ArrayList<GuildMember>();
            for (GuildMember m : g.getMembers()) {
                if (m.getRank().equalsIgnoreCase(rank)) {
                    inRank.add(m);
                }
            }
            if (inRank.isEmpty()) {
                continue;
            }
            Collections.sort(inRank, new Comparator<GuildMember>() {
                @Override
                public int compare(GuildMember x, GuildMember y) {
                    return x.getName().compareToIgnoreCase(y.getName());
                }
            });
            send(p, "&6-- " + rank + " --");
            StringBuilder sb = new StringBuilder();
            for (GuildMember m : inRank) {
                Player pl = Bukkit.getPlayer(m.getUuid());
                boolean isOn = pl != null && pl.isOnline();
                if (isOn) {
                    online++;
                }
                sb.append("&f").append(m.getName()).append(' ').append(isOn ? "&a" : "&c").append(DOT).append("  ");
            }
            send(p, sb.toString().trim());
        }
        send(p, "");
        send(p, "&eTotal Members: &f" + g.size() + "  &eOnline: &a" + online);
        send(p, LINE);
    }

    // ====================================================================== info

    private void info(Player p, String[] a) {
        Guild g = a.length >= 2 ? gm.getGuild(a[1]) : gm.getGuildOf(p.getUniqueId());
        if (g == null) {
            if (a.length >= 2) {
                err(p, "No guild named '" + a[1] + "' exists.");
            } else {
                err(p, "You are not in a guild! Create one with /g create <name>.");
            }
            return;
        }
        GuildMember master = g.getMasterMember();
        List<String> ranks = new ArrayList<String>();
        ranks.add(Guild.MASTER_RANK);
        ranks.addAll(g.getRanks());

        send(p, LINE);
        send(p, "&6Guild: &b" + g.getName());
        send(p, "&eGuild Master: &f" + (master == null ? "Unknown" : master.getName()));
        send(p, "&eMembers: &f" + g.size() + "&7/" + plugin.getMaxMembers());
        send(p, "&eOnline: &a" + g.getOnlineCount());
        send(p, "&eCreated: &f" + new SimpleDateFormat("yyyy-MM-dd").format(new Date(g.getCreated())));
        send(p, "&eRanks: &f" + join(ranks, "&7, &f"));
        GuildMember me = g.getMember(p.getUniqueId());
        if (me != null) {
            send(p, "&eYour rank: &f" + me.getRank());
        }
        send(p, LINE);
    }

    // ====================================================================== help

    private void help(Player p) {
        send(p, LINE);
        send(p, "&6Guild Commands &7(/g, /guild)");
        helpLine(p, "create <name>", "Create a guild");
        helpLine(p, "invite <player>", "Invite a player (Officer+)");
        helpLine(p, "join <guild>", "Accept an invite / request to join");
        helpLine(p, "chat <message>", "Guild chat (also /gc <message>)");
        helpLine(p, "list", "Show members and online status");
        helpLine(p, "info [guild]", "Show guild information");
        helpLine(p, "leave", "Leave your guild");
        helpLine(p, "kick <player> [reason]", "Kick a lower-ranked member (Officer+)");
        helpLine(p, "promote <player>", "Promote a lower-ranked member (Officer+)");
        helpLine(p, "demote <player>", "Demote a lower-ranked member (Officer+)");
        helpLine(p, "createrank <name>", "Create a custom rank (Master)");
        helpLine(p, "deleterank <name>", "Delete a custom rank (Master)");
        helpLine(p, "transfer <player>", "Make someone else Guild Master (Master)");
        helpLine(p, "disband", "Disband the guild (Master)");
        send(p, LINE);
    }

    private void helpLine(Player p, String usage, String description) {
        send(p, "&e/g " + usage + " &7- &f" + description);
    }

    // ============================================================== tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player) || cmd.getName().equalsIgnoreCase("gc")) {
            return Collections.emptyList();
        }
        Player p = (Player) sender;
        Guild g = gm.getGuildOf(p.getUniqueId());

        if (args.length == 1) {
            List<String> subs = new ArrayList<String>(SUBCOMMANDS);
            if (sender.hasPermission("guild.admin")) {
                subs.add("admindisband");
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<String>();
            switch (sub) {
                case "invite":
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (gm.getGuildOf(online.getUniqueId()) == null) {
                            options.add(online.getName());
                        }
                    }
                    break;
                case "kick":
                case "promote":
                case "demote":
                case "transfer":
                    if (g != null) {
                        for (GuildMember m : g.getMembers()) {
                            options.add(m.getName());
                        }
                    }
                    break;
                case "deleterank":
                    if (g != null) {
                        for (String r : g.getRanks()) {
                            if (g.isCustomRank(r)) {
                                options.add(r);
                            }
                        }
                    }
                    break;
                case "join":
                case "accept":
                    options.addAll(gm.getInvitedGuildNames(p.getUniqueId()));
                    break;
                case "info":
                case "admindisband":
                    for (Guild each : gm.getGuilds()) {
                        options.add(each.getName());
                    }
                    break;
                case "disband":
                    options.add("confirm");
                    break;
                default:
                    break;
            }
            return filter(options, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(Collection<String> options, String prefix) {
        List<String> out = new ArrayList<String>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(o);
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    // ================================================================== helpers

    private Guild requireGuild(Player p) {
        Guild g = gm.getGuildOf(p.getUniqueId());
        if (g == null) {
            err(p, "You are not in a guild! Create one with /g create <name>.");
        }
        return g;
    }

    private boolean requireMaster(Player p, Guild g) {
        if (!g.isMaster(p.getUniqueId())) {
            err(p, "Only the Guild Master can do that.");
            return false;
        }
        return true;
    }

    /** Guild Master or Officer. */
    private boolean isStaff(Guild g, UUID uuid) {
        return g.getLevel(uuid) <= 1;
    }

    private void broadcast(Guild g, String text) {
        String line = GuildPlugin.color("&2Guild > &r" + text);
        for (Player member : g.getOnlinePlayers()) {
            member.sendMessage(line);
        }
    }

    private void broadcastExcept(Guild g, UUID except, String text) {
        String line = GuildPlugin.color("&2Guild > &r" + text);
        for (Player member : g.getOnlinePlayers()) {
            if (!member.getUniqueId().equals(except)) {
                member.sendMessage(line);
            }
        }
    }

    private void send(CommandSender to, String text) {
        to.sendMessage(GuildPlugin.color(text));
    }

    private void err(CommandSender to, String text) {
        send(to, "&c" + text);
    }

    private String join(List<String> parts, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
