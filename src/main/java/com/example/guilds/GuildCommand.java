package com.example.guilds;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Handles /guild (/g) and /gc. */
public class GuildCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "create", "disband", "invite", "join", "accept", "chat", "createrank", "deleterank", "ranks",
            "promote", "demote", "leave", "kick", "list", "info", "transfer", "color", "tab", "help");

    private final GuildPlugin plugin;
    private final GuildManager gm;
    private final Map<UUID, Long> disbandConfirm = new HashMap<>();

    public GuildCommand(GuildPlugin plugin) {
        this.plugin = plugin;
        this.gm = plugin.getGuildManager();
    }

    // ------------------------------------------------------------ dispatcher

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use guild commands.");
            return true;
        }
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("gc")) {
            chat(p, args, 0);
            return true;
        }
        if (args.length == 0) { help(p); return true; }

        switch (args[0].toLowerCase()) {
            case "create": create(p, args); break;
            case "disband": disband(p); break;
            case "invite": invite(p, args); break;
            case "join": join(p, args); break;
            case "accept": accept(p, args); break;
            case "chat": case "c": chat(p, args, 1); break;
            case "createrank": createRank(p, args); break;
            case "deleterank": deleteRank(p, args); break;
            case "ranks": ranks(p); break;
            case "promote": moveRank(p, args, true); break;
            case "demote": moveRank(p, args, false); break;
            case "leave": leave(p); break;
            case "kick": kick(p, args); break;
            case "list": case "members": list(p); break;
            case "info": info(p); break;
            case "transfer": transfer(p, args); break;
            case "color": case "colour": openGui(p, true); break;
            case "tab": openGui(p, false); break;
            case "help": help(p); break;
            default: Msg.error(p, "Unknown sub-command. Use /g help."); break;
        }
        return true;
    }

    // --------------------------------------------------------------- helpers

    private Guild need(Player p) {
        Guild g = gm.getGuild(p.getUniqueId());
        if (g == null) Msg.error(p, "You are not in a guild!");
        return g;
    }

    private boolean canManage(Guild g, Player p) {
        UUID u = p.getUniqueId();
        return g.isMaster(u) || Guild.OFFICER.equals(g.getRank(u));
    }

    private long ttl() {
        return plugin.getConfig().getInt("settings.invite-expire-seconds", 60) * 1000L;
    }

    private void addToGuild(Guild g, Player p) {
        gm.clearInvites(p.getUniqueId());
        gm.clearRequests(p.getUniqueId());
        gm.addMember(g, p.getUniqueId(), p.getName());
        gm.save();
        plugin.getTabManager().apply(p);
        plugin.broadcast(g, Msg.color(Msg.PREFIX + "&a" + p.getName() + " &ejoined the guild!"));
    }

    private void clickable(Player to, String text, String command, String hover) {
        TextComponent c = new TextComponent(Msg.color(text));
        c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[]{new TextComponent(Msg.color(hover))}));
        to.spigot().sendMessage(c);
    }

    // -------------------------------------------------------------- commands

    private void help(Player p) {
        Msg.send(p, Msg.line());
        Msg.send(p, "&2Guild Commands");
        String[][] h = {
                {"create <name>", "Create a guild"},
                {"invite <player>", "Invite a player"},
                {"join <guild>", "Accept an invite / request to join"},
                {"accept <player>", "Accept a join request"},
                {"chat <message>", "Guild chat (no message = toggle)"},
                {"list", "Members and online status"},
                {"info", "Guild information"},
                {"ranks", "Show the rank hierarchy"},
                {"promote / demote <player>", "Change a member's rank (Master)"},
                {"createrank / deleterank <name>", "Manage custom ranks (Master)"},
                {"kick <player>", "Kick a member"},
                {"transfer <player>", "Give the guild to a member (Master)"},
                {"color", "Open the guild color GUI (Master)"},
                {"tab", "Open the tab settings GUI (Master)"},
                {"leave", "Leave your guild"},
                {"disband", "Disband your guild (Master)"},
        };
        for (String[] x : h) Msg.send(p, "&e/g " + x[0] + " &7- &f" + x[1]);
        Msg.send(p, "&e/gc <message> &7- &fSend a guild chat message");
        Msg.send(p, Msg.line());
    }

    private void create(Player p, String[] a) {
        if (!p.hasPermission("guild.create")) { Msg.error(p, "You don't have permission to create a guild."); return; }
        if (a.length < 2) { Msg.error(p, "Usage: /g create <name>"); return; }
        if (gm.getGuild(p.getUniqueId()) != null) { Msg.error(p, "You are already in a guild!"); return; }

        int min = plugin.getConfig().getInt("settings.name-min", 3);
        int max = plugin.getConfig().getInt("settings.name-max", 16);
        String name = a[1];
        if (!name.matches("[A-Za-z0-9_]+") || name.length() < min || name.length() > max) {
            Msg.error(p, "Guild names must be " + min + "-" + max + " characters (letters, numbers, underscore).");
            return;
        }
        if (gm.exists(name)) { Msg.error(p, "A guild with that name already exists."); return; }

        Guild g = gm.createGuild(name, p);
        gm.save();
        plugin.getTabManager().apply(p);

        Msg.send(p, Msg.line());
        Msg.send(p, "&aYou created the guild &6" + g.getName() + "&a!");
        Msg.send(p, "&eUse &6/g color &eto pick a guild color and &6/g tab &eto choose the tab display.");
        Msg.send(p, Msg.line());
    }

    private void disband(Player p) {
        Guild g = need(p);
        if (g == null) return;
        if (!g.isMaster(p.getUniqueId())) { Msg.error(p, "Only the Guild Master can disband the guild."); return; }

        long now = System.currentTimeMillis();
        Long t = disbandConfirm.get(p.getUniqueId());
        if (t == null || now - t > 15000L) {
            disbandConfirm.put(p.getUniqueId(), now);
            Msg.send(p, "&eAre you sure? Type &c/g disband &eagain within 15 seconds to confirm.");
            return;
        }
        disbandConfirm.remove(p.getUniqueId());

        plugin.broadcast(g, Msg.color("&cThe guild &6" + g.getName() + " &cwas disbanded by " + p.getName() + "."));
        List<UUID> members = new ArrayList<UUID>(g.getMembers());
        gm.disband(g);
        gm.save();
        for (UUID u : members) {
            plugin.getChatToggled().remove(u);
            Player m = Bukkit.getPlayer(u);
            if (m != null) plugin.getTabManager().remove(m);
        }
    }

    private void invite(Player p, String[] a) {
        Guild g = need(p);
        if (g == null) return;
        if (!canManage(g, p)) { Msg.error(p, "You must be the Guild Master or an Officer to invite players."); return; }
        if (a.length < 2) { Msg.error(p, "Usage: /g invite <player>"); return; }

        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { Msg.error(p, "That player is not online."); return; }
        if (t.equals(p)) { Msg.error(p, "You can't invite yourself."); return; }
        if (gm.getGuild(t.getUniqueId()) != null) { Msg.error(p, "That player is already in a guild."); return; }
        if (gm.hasInvite(t.getUniqueId(), g)) { Msg.error(p, "That player already has a pending invite."); return; }

        gm.addInvite(t.getUniqueId(), g, ttl());
        plugin.broadcast(g, Msg.color(Msg.PREFIX + "&a" + p.getName() + " &einvited &a" + t.getName()
                + " &eto the guild! They have " + (ttl() / 1000) + " seconds to accept."));

        Msg.send(t, Msg.line());
        Msg.send(t, "&a" + p.getName() + " &ehas invited you to join the guild &b" + g.getName() + "&e!");
        clickable(t, "&6&lCLICK HERE &eto join, or run &6/g join " + g.getName(),
                "/g join " + g.getName(), "&eClick to join " + g.getName());
        Msg.send(t, Msg.line());
    }

    private void join(Player p, String[] a) {
        if (a.length < 2) { Msg.error(p, "Usage: /g join <guild name>"); return; }
        if (gm.getGuild(p.getUniqueId()) != null) {
            Msg.error(p, "You are already in a guild! Leave it first with /g leave.");
            return;
        }
        Guild g = gm.getGuildByName(a[1]);
        if (g == null) { Msg.error(p, "That guild doesn't exist."); return; }

        if (gm.hasInvite(p.getUniqueId(), g)) { addToGuild(g, p); return; }

        if (gm.hasRequest(g, p.getUniqueId())) { Msg.error(p, "You already requested to join that guild."); return; }
        gm.addRequest(g, p.getUniqueId(), ttl());
        Msg.send(p, Msg.PREFIX + "&eYou requested to join &b" + g.getName()
                + "&e. An Officer or the Guild Master has to accept it.");

        for (UUID u : g.getMembers()) {
            Player m = Bukkit.getPlayer(u);
            if (m == null || !canManage(g, m)) continue;
            Msg.send(m, Msg.PREFIX + "&a" + p.getName() + " &ewants to join the guild!");
            clickable(m, "&6&lCLICK HERE &eto accept, or run &6/g accept " + p.getName(),
                    "/g accept " + p.getName(), "&eClick to accept " + p.getName());
        }
    }

    private void accept(Player p, String[] a) {
        Guild g = need(p);
        if (g == null) return;
        if (!canManage(g, p)) { Msg.error(p, "You must be the Guild Master or an Officer to accept requests."); return; }
        if (a.length < 2) { Msg.error(p, "Usage: /g accept <player>"); return; }

        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { Msg.error(p, "That player must be online to be accepted."); return; }
        if (!gm.hasRequest(g, t.getUniqueId())) { Msg.error(p, "That player has not requested to join your guild."); return; }
        if (gm.getGuild(t.getUniqueId()) != null) {
            gm.clearRequests(t.getUniqueId());
            Msg.error(p, "That player is already in a guild.");
            return;
        }
        addToGuild(g, t);
    }

    private void chat(Player p, String[] a, int from) {
        Guild g = need(p);
        if (g == null) return;

        if (a.length <= from) { // toggle mode
            Set<UUID> t = plugin.getChatToggled();
            if (t.remove(p.getUniqueId())) {
                Msg.send(p, Msg.PREFIX + "&cGuild chat mode disabled. You talk in public chat again.");
            } else {
                t.add(p.getUniqueId());
                Msg.send(p, Msg.PREFIX + "&aGuild chat mode enabled. Use &e/g chat &aagain to disable it.");
            }
            return;
        }
        plugin.sendGuildChat(p, Msg.join(a, from));
    }

    private void createRank(Player p, String[] a) {
        Guild g = need(p);
        if (g == null) return;
        if (!g.isMaster(p.getUniqueId())) { Msg.error(p, "Only the Guild Master can create ranks."); return; }
        if (a.length < 2) { Msg.error(p, "Usage: /g createrank <rankName>"); return; }

        String n = a[1];
        if (!n.matches("[A-Za-z0-9_]{1,16}")) {
            Msg.error(p, "Rank names must be 1-16 characters (letters, numbers, underscore).");
            return;
        }
        if (g.findRank(n) != null) { Msg.error(p, "That rank already exists."); return; }
        if (g.getRanks().size() >= plugin.getConfig().getInt("settings.max-ranks", 10)) {
            Msg.error(p, "Your guild has reached the maximum number of ranks.");
            return;
        }
        g.addRank(n);
        gm.save();
        Msg.send(p, Msg.PREFIX + "&aCreated rank &b" + n + "&a (placed just above Member). Use &e/g promote <player> &ato assign it.");
    }

    private void deleteRank(Player p, String[] a) {
        Guild g = need(p);
        if (g == null) return;
        if (!g.isMaster(p.getUniqueId())) { Msg.error(p, "Only the Guild Master can delete ranks."); return; }
        if (a.length < 2) { Msg.error(p, "Usage: /g deleterank <rankName>"); return; }

        String r = g.findRank(a[1]);
        if (r == null) { Msg.error(p, "That rank doesn't exist."); return; }
        if (Guild.isDefaultRank(r)) { Msg.error(p, "The default ranks (Guild Master, Officer, Member) can't be deleted."); return; }

        g.removeRank(r);
        gm.save();
        plugin.getTabManager().refreshGuild(g);
        plugin.broadcast(g, Msg.color(Msg.PREFIX + "&eThe rank &b" + r + " &ewas deleted. Its members are now &bMember&e."));
    }

    private void ranks(Player p) {
        Guild g = need(p);
        if (g == null) return;
        Msg.send(p, Msg.line());
        Msg.send(p, "&2Guild ranks &7(highest -> lowest)");
        Msg.send(p, "&6" + Guild.MASTER_RANK + " &7- &f" + countRank(g, Guild.MASTER_RANK) + " member(s)");
        for (String r : g.getRanks()) Msg.send(p, "&b" + r + " &7- &f" + countRank(g, r) + " member(s)");
        Msg.send(p, Msg.line());
    }

    private int countRank(Guild g, String rank) {
        int n = 0;
        for (UUID u : g.getMembers()) if (g.getRank(u).equals(rank)) n++;
        return n;
    }

    private void moveRank(Player p, String[] a, boolean up) {
        Guild g = need(p);
        if (g == null) return;
        if (!g.isMaster(p.getUniqueId())) { Msg.error(p, "Only the Guild Master can manage ranks."); return; }
        if (a.length < 2) { Msg.error(p, "Usage: /g " + (up ? "promote" : "demote") + " <player>"); return; }

        UUID t = g.findMember(a[1]);
        if (t == null) { Msg.error(p, "That player is not in your guild."); return; }
        if (t.equals(p.getUniqueId())) { Msg.error(p, "You can't change your own rank."); return; }

        List<String> ranks = g.getRanks();
        int idx = g.rankIndex(t);
        int ni = up ? idx - 1 : idx + 1;
        String name = g.getMemberName(t);

        if (up && ni < 0) {
            Msg.error(p, name + " already has the highest rank. Use /g transfer to hand over the guild.");
            return;
        }
        if (!up && ni >= ranks.size()) { Msg.error(p, name + " already has the lowest rank."); return; }

        String old = g.getRank(t);
        String now = ranks.get(ni);
        g.setRank(t, now);
        gm.save();

        Player online = Bukkit.getPlayer(t);
        if (online != null) plugin.getTabManager().apply(online);

        plugin.broadcast(g, Msg.color(Msg.PREFIX + "&a" + name + " &ewas " + (up ? "promoted" : "demoted")
                + " from &b" + old + " &eto &b" + now + "&e!"));
    }

    private void leave(Player p) {
        Guild g = need(p);
        if (g == null) return;
        if (g.isMaster(p.getUniqueId())) {
            Msg.error(p, "You are the Guild Master! Use /g transfer <player> or /g disband.");
            return;
        }
        gm.removeMember(g, p.getUniqueId());
        gm.save();
        plugin.getTabManager().remove(p);
        plugin.getChatToggled().remove(p.getUniqueId());

        Msg.send(p, Msg.PREFIX + "&eYou left the guild &b" + g.getName() + "&e.");
        plugin.broadcast(g, Msg.color(Msg.PREFIX + "&a" + p.getName() + " &eleft the guild!"));
    }

    private void kick(Player p, String[] a) {
        Guild g = need(p);
        if (g == null) return;
        if (!canManage(g, p)) { Msg.error(p, "You must be the Guild Master or an Officer to kick players."); return; }
        if (a.length < 2) { Msg.error(p, "Usage: /g kick <player>"); return; }

        UUID t = g.findMember(a[1]);
        if (t == null) { Msg.error(p, "That player is not in your guild."); return; }
        if (t.equals(p.getUniqueId())) { Msg.error(p, "You can't kick yourself. Use /g leave."); return; }
        if (g.isMaster(t)) { Msg.error(p, "You can't kick the Guild Master."); return; }
        if (!g.isMaster(p.getUniqueId()) && g.rankIndex(t) <= g.rankIndex(p.getUniqueId())) {
            Msg.error(p, "You can only kick members with a lower rank than yours.");
            return;
        }

        String tn = g.getMemberName(t);
        plugin.broadcast(g, Msg.color(Msg.PREFIX + "&a" + tn + " &cwas kicked from the guild by &a" + p.getName() + "&c!"));

        gm.removeMember(g, t);
        gm.save();
        plugin.getChatToggled().remove(t);
        Player online = Bukkit.getPlayer(t);
        if (online != null) plugin.getTabManager().remove(online);
    }

    private void list(Player p) {
        Guild g = need(p);
        if (g == null) return;

        Msg.send(p, Msg.line());
        Msg.send(p, "&" + g.getColor() + g.getName() + " &2- Members (&a" + g.size() + "&2)");

        List<String> order = new ArrayList<String>();
        order.add(Guild.MASTER_RANK);
        order.addAll(g.getRanks());

        for (String rank : order) {
            StringBuilder sb = new StringBuilder();
            for (UUID u : g.getMembers()) {
                if (!g.getRank(u).equals(rank)) continue;
                boolean on = Bukkit.getPlayer(u) != null;
                sb.append(on ? "&f" : "&7").append(g.getMemberName(u)).append(on ? " &a\u25CF  " : " &c\u25CF  ");
            }
            if (sb.length() == 0) continue;
            Msg.send(p, " ");
            Msg.send(p, "&6-- " + rank + " --");
            Msg.send(p, sb.toString());
        }
        Msg.send(p, Msg.line());
    }

    private void info(Player p) {
        Guild g = need(p);
        if (g == null) return;

        int online = 0;
        for (UUID u : g.getMembers()) if (Bukkit.getPlayer(u) != null) online++;

        Msg.send(p, Msg.line());
        Msg.send(p, "&2Guild: &" + g.getColor() + g.getName());
        Msg.send(p, "&2Guild Master: &f" + g.getMemberName(g.getMaster()));
        Msg.send(p, "&2Members: &f" + g.size() + " &7(&a" + online + " online&7)");
        Msg.send(p, "&2Your rank: &f" + g.getRank(p.getUniqueId()));
        Msg.send(p, "&2Color: &" + g.getColor() + GuiManager.colorName(g.getColor()));
        Msg.send(p, "&2Tab display: &f" + g.getTabMode().getDisplay());
        Msg.send(p, "&2Ranks: &f" + Guild.MASTER_RANK + ", " + join(g.getRanks()));
        Msg.send(p, "&2Created: &f" + new SimpleDateFormat("yyyy-MM-dd").format(new Date(g.getCreated())));
        Msg.send(p, Msg.line());
    }

    private String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    private void transfer(Player p, String[] a) {
        Guild g = need(p);
        if (g == null) return;
        if (!g.isMaster(p.getUniqueId())) { Msg.error(p, "Only the Guild Master can transfer the guild."); return; }
        if (a.length < 2) { Msg.error(p, "Usage: /g transfer <player>"); return; }

        UUID t = g.findMember(a[1]);
        if (t == null) { Msg.error(p, "That player is not in your guild."); return; }
        if (t.equals(p.getUniqueId())) { Msg.error(p, "You are already the Guild Master."); return; }

        g.transferMaster(t);
        gm.save();
        plugin.getTabManager().refreshGuild(g);
        plugin.broadcast(g, Msg.color(Msg.PREFIX + "&a" + p.getName() + " &etransferred the guild to &a"
                + g.getMemberName(t) + "&e!"));
    }

    private void openGui(Player p, boolean color) {
        Guild g = need(p);
        if (g == null) return;
        if (!p.hasPermission(color ? "guild.color" : "guild.tab")) { Msg.error(p, "You don't have permission."); return; }
        if (!g.isMaster(p.getUniqueId())) { Msg.error(p, "Only the Guild Master can change this."); return; }
        if (color) plugin.getGuiManager().openColor(p, g);
        else plugin.getGuiManager().openTab(p, g);
    }

    // ----------------------------------------------------------- tab complete

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player) || c.getName().equalsIgnoreCase("gc")) return Collections.<String>emptyList();
        Player p = (Player) s;
        Guild g = gm.getGuild(p.getUniqueId());

        if (a.length == 1) return filter(SUBS, a[0]);

        if (a.length == 2) {
            List<String> pool = new ArrayList<String>();
            switch (a[0].toLowerCase()) {
                case "invite":
                case "accept":
                    for (Player o : Bukkit.getOnlinePlayers()) pool.add(o.getName());
                    break;
                case "join":
                    for (Guild x : gm.all()) pool.add(x.getName());
                    break;
                case "promote":
                case "demote":
                case "kick":
                case "transfer":
                    if (g != null) for (UUID u : g.getMembers()) pool.add(g.getMemberName(u));
                    break;
                case "deleterank":
                    if (g != null) for (String r : g.getRanks()) if (!Guild.isDefaultRank(r)) pool.add(r);
                    break;
                default:
                    break;
            }
            return filter(pool, a[1]);
        }
        return Collections.<String>emptyList();
    }

    private List<String> filter(List<String> src, String start) {
        List<String> out = new ArrayList<String>();
        for (String x : src) if (x.toLowerCase().startsWith(start.toLowerCase())) out.add(x);
        return out;
    }
}
