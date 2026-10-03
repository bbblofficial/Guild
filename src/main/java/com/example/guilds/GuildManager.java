package com.example.guilds;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Holds all guilds, handles guilds.yml persistence, invites and join requests. */
public class GuildManager {

    private final GuildPlugin plugin;
    private final File file;

    private final Map<String, Guild> guilds = new LinkedHashMap<>();
    private final Map<UUID, String> playerGuild = new HashMap<>();
    /** invited player -> (guild key -> expiry) */
    private final Map<UUID, Map<String, Long>> invites = new HashMap<>();
    /** guild key -> (requesting player -> expiry) */
    private final Map<String, Map<UUID, Long>> requests = new HashMap<>();

    public GuildManager(GuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "guilds.yml");
    }

    private static String key(String name) { return name.toLowerCase(); }

    // ------------------------------------------------------------- lookups

    public Guild getGuild(UUID player) {
        String k = playerGuild.get(player);
        return k == null ? null : guilds.get(k);
    }

    public Guild getGuildByName(String name) { return guilds.get(key(name)); }
    public boolean exists(String name) { return guilds.containsKey(key(name)); }
    public Collection<Guild> all() { return guilds.values(); }

    // ------------------------------------------------------------ mutation

    public Guild createGuild(String name, Player master) {
        Guild g = new Guild(name, master.getUniqueId(), master.getName(), System.currentTimeMillis());
        guilds.put(key(name), g);
        playerGuild.put(master.getUniqueId(), key(name));
        return g;
    }

    public void disband(Guild g) {
        String k = key(g.getName());
        for (UUID u : g.getMembers()) playerGuild.remove(u);
        guilds.remove(k);
        requests.remove(k);
        for (Map<String, Long> m : invites.values()) m.remove(k);
    }

    public void addMember(Guild g, UUID u, String name) {
        g.addMember(u, name, Guild.MEMBER);
        playerGuild.put(u, key(g.getName()));
    }

    public void removeMember(Guild g, UUID u) {
        g.removeMember(u);
        playerGuild.remove(u);
    }

    // ------------------------------------------------------------- invites

    public void addInvite(UUID target, Guild g, long ttlMs) {
        Map<String, Long> m = invites.get(target);
        if (m == null) { m = new HashMap<>(); invites.put(target, m); }
        m.put(key(g.getName()), System.currentTimeMillis() + ttlMs);
    }

    public boolean hasInvite(UUID target, Guild g) {
        Map<String, Long> m = invites.get(target);
        if (m == null) return false;
        Long exp = m.get(key(g.getName()));
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) { m.remove(key(g.getName())); return false; }
        return true;
    }

    public void clearInvites(UUID target) { invites.remove(target); }

    // ------------------------------------------------------------ requests

    public void addRequest(Guild g, UUID requester, long ttlMs) {
        Map<UUID, Long> m = requests.get(key(g.getName()));
        if (m == null) { m = new HashMap<>(); requests.put(key(g.getName()), m); }
        m.put(requester, System.currentTimeMillis() + ttlMs);
    }

    public boolean hasRequest(Guild g, UUID requester) {
        Map<UUID, Long> m = requests.get(key(g.getName()));
        if (m == null) return false;
        Long exp = m.get(requester);
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) { m.remove(requester); return false; }
        return true;
    }

    public void clearRequests(UUID requester) {
        for (Map<UUID, Long> m : requests.values()) m.remove(requester);
    }

    // --------------------------------------------------------- persistence

    public void load() {
        guilds.clear();
        playerGuild.clear();
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("guilds");
        if (root == null) return;

        for (String k : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(k);
                String name = s.getString("name", k);
                UUID master = UUID.fromString(s.getString("master"));
                String masterName = s.getString("members." + master + ".name", "Unknown");
                Guild g = new Guild(name, master, masterName, s.getLong("created", System.currentTimeMillis()));

                String col = s.getString("color", "a");
                if (!col.isEmpty()) g.setColor(col.charAt(0));
                try {
                    g.setTabMode(TabMode.valueOf(s.getString("tab-mode", "NAME").toUpperCase()));
                } catch (IllegalArgumentException ignored) { }

                List<String> ranks = s.getStringList("ranks");
                if (!ranks.isEmpty()) g.setRanks(ranks);

                ConfigurationSection members = s.getConfigurationSection("members");
                if (members != null) {
                    for (String id : members.getKeys(false)) {
                        UUID u = UUID.fromString(id);
                        String rank = members.getString(id + ".rank", Guild.MEMBER);
                        if (u.equals(master)) rank = Guild.MASTER_RANK;
                        g.addMember(u, members.getString(id + ".name", "Unknown"), rank);
                    }
                }

                guilds.put(key(name), g);
                for (UUID u : g.getMembers()) playerGuild.put(u, key(name));
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not load guild '" + k + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + guilds.size() + " guild(s).");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Guild g : guilds.values()) {
            String p = "guilds." + key(g.getName()) + ".";
            yml.set(p + "name", g.getName());
            yml.set(p + "master", g.getMaster().toString());
            yml.set(p + "created", g.getCreated());
            yml.set(p + "color", String.valueOf(g.getColor()));
            yml.set(p + "tab-mode", g.getTabMode().name());
            yml.set(p + "ranks", new ArrayList<>(g.getRanks()));
            for (UUID u : g.getMembers()) {
                yml.set(p + "members." + u + ".name", g.getMemberName(u));
                yml.set(p + "members." + u + ".rank", g.getRank(u));
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save guilds.yml: " + e.getMessage());
        }
    }
}
