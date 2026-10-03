package com.example.guilds;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Holds every guild in memory, handles invites / join requests and persists
 * everything to plugins/Guilds/guilds.yml.
 */
public class GuildManager {

    private final GuildPlugin plugin;
    private final File file;

    private final Map<String, Guild> guilds = new HashMap<String, Guild>();      // lower-case name -> guild
    private final Map<UUID, Guild> playerIndex = new HashMap<UUID, Guild>();     // player -> guild

    // target player -> (guild key -> expiry millis)
    private final Map<UUID, Map<String, Long>> invites = new HashMap<UUID, Map<String, Long>>();
    // guild key -> (requesting player -> expiry millis)
    private final Map<String, Map<UUID, Long>> requests = new HashMap<String, Map<UUID, Long>>();

    private final Object ioLock = new Object();
    private long saveSeq = 0;
    private long writtenSeq = 0;

    public GuildManager(GuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "guilds.yml");
    }

    // ================================================================ storage

    public void load() {
        guilds.clear();
        playerIndex.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("guilds");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                Guild guild = readGuild(key, s);
                guilds.put(guild.getName().toLowerCase(Locale.ROOT), guild);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Skipping malformed guild entry '" + key + "' in guilds.yml", ex);
            }
        }
        plugin.getLogger().info("Loaded " + guilds.size() + " guild(s).");
    }

    private Guild readGuild(String key, ConfigurationSection s) {
        String name = s.getString("name", key);
        UUID master = UUID.fromString(s.getString("master"));
        Guild guild = new Guild(name, s.getLong("created", System.currentTimeMillis()));

        // Sanitise the rank list: Officer first, Member last, no duplicates, no master rank.
        List<String> ranks = new ArrayList<String>();
        for (String r : s.getStringList("ranks")) {
            if (r == null || r.trim().isEmpty()) {
                continue;
            }
            r = r.trim();
            if (Guild.MASTER_RANK.equalsIgnoreCase(r)
                    || Guild.OFFICER_RANK.equalsIgnoreCase(r)
                    || Guild.DEFAULT_RANK.equalsIgnoreCase(r)) {
                continue;
            }
            boolean dup = false;
            for (String existing : ranks) {
                if (existing.equalsIgnoreCase(r)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                ranks.add(r);
            }
        }
        ranks.add(0, Guild.OFFICER_RANK);
        ranks.add(Guild.DEFAULT_RANK);
        guild.setRanks(ranks);
        guild.setMaster(master);

        ConfigurationSection ms = s.getConfigurationSection("members");
        if (ms != null) {
            for (String uuidStr : ms.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    if (playerIndex.containsKey(uuid)) {
                        plugin.getLogger().warning("Player " + uuid + " is in more than one guild in guilds.yml; ignoring the entry in '" + name + "'.");
                        continue;
                    }
                    String memberName = ms.getString(uuidStr + ".name", "Unknown");
                    String rank = guild.canonicalRank(ms.getString(uuidStr + ".rank", Guild.DEFAULT_RANK));
                    if (rank == null) {
                        rank = Guild.DEFAULT_RANK;
                    }
                    if (uuid.equals(master)) {
                        rank = Guild.MASTER_RANK;
                    } else if (Guild.MASTER_RANK.equals(rank)) {
                        rank = Guild.OFFICER_RANK;
                    }
                    guild.addMember(new GuildMember(uuid, memberName, rank, ms.getLong(uuidStr + ".joined", guild.getCreated())));
                    playerIndex.put(uuid, guild);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Bad member UUID '" + uuidStr + "' in guild '" + name + "'.");
                }
            }
        }

        // Make sure the master is always a member.
        if (guild.getMember(master) == null) {
            if (playerIndex.containsKey(master)) {
                throw new IllegalStateException("master " + master + " already belongs to another guild");
            }
            guild.addMember(new GuildMember(master, "Unknown", Guild.MASTER_RANK, guild.getCreated()));
            playerIndex.put(master, guild);
        }
        return guild;
    }

    /** Serialises on the calling (main) thread, writes to disk asynchronously. */
    public void save() {
        final String data = serialise();
        final long seq = ++saveSeq;
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    write(data, seq);
                }
            });
        } else {
            write(data, seq);
        }
    }

    /** Synchronous save, used on shutdown. */
    public void saveNow() {
        write(serialise(), ++saveSeq);
    }

    private String serialise() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("version", 1);
        for (Guild g : guilds.values()) {
            String base = "guilds." + g.getName().toLowerCase(Locale.ROOT);
            yml.set(base + ".name", g.getName());
            yml.set(base + ".master", g.getMaster().toString());
            yml.set(base + ".created", g.getCreated());
            yml.set(base + ".ranks", new ArrayList<String>(g.getRanks()));
            for (GuildMember m : g.getMembers()) {
                String mb = base + ".members." + m.getUuid().toString();
                yml.set(mb + ".name", m.getName());
                yml.set(mb + ".rank", m.getRank());
                yml.set(mb + ".joined", m.getJoined());
            }
        }
        return yml.saveToString();
    }

    private void write(String data, long seq) {
        synchronized (ioLock) {
            if (seq < writtenSeq) {
                return; // a newer snapshot was already written
            }
            try {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                File tmp = new File(dir, "guilds.yml.tmp");
                Files.write(tmp.toPath(), data.getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                writtenSeq = seq;
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save guilds.yml", ex);
            }
        }
    }

    // ================================================================= lookup

    public Guild getGuild(String name) {
        return name == null ? null : guilds.get(name.toLowerCase(Locale.ROOT));
    }

    public Guild getGuildOf(UUID uuid) {
        return playerIndex.get(uuid);
    }

    public Collection<Guild> getGuilds() {
        return guilds.values();
    }

    // ============================================================== mutations

    public Guild createGuild(String name, Player master) {
        Guild guild = new Guild(name, System.currentTimeMillis());
        guild.setMaster(master.getUniqueId());
        guild.addMember(new GuildMember(master.getUniqueId(), master.getName(), Guild.MASTER_RANK, guild.getCreated()));
        guilds.put(name.toLowerCase(Locale.ROOT), guild);
        playerIndex.put(master.getUniqueId(), guild);
        save();
        return guild;
    }

    public void disband(Guild guild) {
        String key = guild.getName().toLowerCase(Locale.ROOT);
        for (GuildMember m : new ArrayList<GuildMember>(guild.getMembers())) {
            playerIndex.remove(m.getUuid());
        }
        guilds.remove(key);
        requests.remove(key);
        for (Map<String, Long> perPlayer : invites.values()) {
            perPlayer.remove(key);
        }
        save();
    }

    public void addMember(Guild guild, Player player) {
        guild.addMember(new GuildMember(player.getUniqueId(), player.getName(), Guild.DEFAULT_RANK, System.currentTimeMillis()));
        playerIndex.put(player.getUniqueId(), guild);
        // joining makes any other pending invite / request obsolete
        invites.remove(player.getUniqueId());
        for (Map<UUID, Long> perGuild : requests.values()) {
            perGuild.remove(player.getUniqueId());
        }
        save();
    }

    public void removeMember(Guild guild, UUID uuid) {
        guild.removeMember(uuid);
        playerIndex.remove(uuid);
        save();
    }

    // ================================================================ invites

    public void addInvite(UUID target, Guild guild, long ttlMillis) {
        Map<String, Long> perPlayer = invites.get(target);
        if (perPlayer == null) {
            perPlayer = new HashMap<String, Long>();
            invites.put(target, perPlayer);
        }
        perPlayer.put(guild.getName().toLowerCase(Locale.ROOT), System.currentTimeMillis() + ttlMillis);
    }

    public boolean hasInvite(UUID target, Guild guild) {
        Map<String, Long> perPlayer = invites.get(target);
        if (perPlayer == null) {
            return false;
        }
        Long expiry = perPlayer.get(guild.getName().toLowerCase(Locale.ROOT));
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /** Returns true (and removes the invite) if a valid invite existed. */
    public boolean consumeInvite(UUID target, Guild guild) {
        boolean valid = hasInvite(target, guild);
        Map<String, Long> perPlayer = invites.get(target);
        if (perPlayer != null) {
            perPlayer.remove(guild.getName().toLowerCase(Locale.ROOT));
        }
        return valid;
    }

    /** Display names of guilds that currently have a valid invite for this player. */
    public List<String> getInvitedGuildNames(UUID target) {
        List<String> names = new ArrayList<String>();
        Map<String, Long> perPlayer = invites.get(target);
        if (perPlayer == null) {
            return names;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : perPlayer.entrySet()) {
            Guild g = guilds.get(e.getKey());
            if (g != null && e.getValue() > now) {
                names.add(g.getName());
            }
        }
        return names;
    }

    // ============================================================== join requests

    public void addRequest(Guild guild, UUID requester, long ttlMillis) {
        String key = guild.getName().toLowerCase(Locale.ROOT);
        Map<UUID, Long> perGuild = requests.get(key);
        if (perGuild == null) {
            perGuild = new HashMap<UUID, Long>();
            requests.put(key, perGuild);
        }
        perGuild.put(requester, System.currentTimeMillis() + ttlMillis);
    }

    public boolean hasRequest(Guild guild, UUID requester) {
        Map<UUID, Long> perGuild = requests.get(guild.getName().toLowerCase(Locale.ROOT));
        if (perGuild == null) {
            return false;
        }
        Long expiry = perGuild.get(requester);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /** Returns true (and removes the request) if a valid request existed. */
    public boolean consumeRequest(Guild guild, UUID requester) {
        boolean valid = hasRequest(guild, requester);
        Map<UUID, Long> perGuild = requests.get(guild.getName().toLowerCase(Locale.ROOT));
        if (perGuild != null) {
            perGuild.remove(requester);
        }
        return valid;
    }

    // ================================================================ cleanup

    public void cleanupExpired() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Map<String, Long>>> it = invites.entrySet().iterator();
        while (it.hasNext()) {
            Map<String, Long> perPlayer = it.next().getValue();
            Iterator<Map.Entry<String, Long>> inner = perPlayer.entrySet().iterator();
            while (inner.hasNext()) {
                if (inner.next().getValue() <= now) {
                    inner.remove();
                }
            }
            if (perPlayer.isEmpty()) {
                it.remove();
            }
        }

        Iterator<Map.Entry<String, Map<UUID, Long>>> rit = requests.entrySet().iterator();
        while (rit.hasNext()) {
            Map<UUID, Long> perGuild = rit.next().getValue();
            Iterator<Map.Entry<UUID, Long>> inner = perGuild.entrySet().iterator();
            while (inner.hasNext()) {
                if (inner.next().getValue() <= now) {
                    inner.remove();
                }
            }
            if (perGuild.isEmpty()) {
                rit.remove();
            }
        }
    }
}
