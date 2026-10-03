package com.example.guilds;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A guild: a master, an ordered list of ranks (highest to lowest, excluding the master)
 * and a set of members.
 *
 * Rank hierarchy: "Guild Master" (level 0) > ranks.get(0) (level 1) > ranks.get(1) (level 2) ...
 * The list always starts with "Officer" and always ends with "Member" (the default rank).
 * Custom ranks are inserted directly above "Member".
 */
public class Guild {

    public static final String MASTER_RANK = "Guild Master";
    public static final String OFFICER_RANK = "Officer";
    public static final String DEFAULT_RANK = "Member";

    private final String name;
    private final long created;
    private UUID master;
    private final List<String> ranks = new ArrayList<String>();
    private final Map<UUID, GuildMember> members = new LinkedHashMap<UUID, GuildMember>();

    public Guild(String name, long created) {
        this.name = name;
        this.created = created;
        ranks.add(OFFICER_RANK);
        ranks.add(DEFAULT_RANK);
    }

    // ------------------------------------------------------------------ basics

    public String getName() {
        return name;
    }

    public long getCreated() {
        return created;
    }

    public UUID getMaster() {
        return master;
    }

    public void setMaster(UUID master) {
        this.master = master;
    }

    public GuildMember getMasterMember() {
        return master == null ? null : members.get(master);
    }

    // ------------------------------------------------------------------- ranks

    /** Ranks below the master, highest first. */
    public List<String> getRanks() {
        return Collections.unmodifiableList(ranks);
    }

    public void setRanks(List<String> newRanks) {
        ranks.clear();
        ranks.addAll(newRanks);
    }

    public int indexOfRank(String rank) {
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).equalsIgnoreCase(rank)) {
                return i;
            }
        }
        return -1;
    }

    /** Returns the stored spelling of a rank (including the master rank), or null if it does not exist. */
    public String canonicalRank(String rank) {
        if (rank == null) {
            return null;
        }
        if (MASTER_RANK.equalsIgnoreCase(rank)) {
            return MASTER_RANK;
        }
        int idx = indexOfRank(rank);
        return idx < 0 ? null : ranks.get(idx);
    }

    public boolean isCustomRank(String rank) {
        return indexOfRank(rank) >= 0
                && !OFFICER_RANK.equalsIgnoreCase(rank)
                && !DEFAULT_RANK.equalsIgnoreCase(rank);
    }

    public int getCustomRankCount() {
        return Math.max(0, ranks.size() - 2);
    }

    /** Adds a custom rank directly above the default rank. Returns false if the name is taken. */
    public boolean addRank(String rank) {
        if (canonicalRank(rank) != null) {
            return false;
        }
        ranks.add(ranks.size() - 1, rank);
        return true;
    }

    /** Removes a custom rank and moves everyone who had it to the default rank. Returns how many were moved. */
    public int removeRank(String rank) {
        int idx = indexOfRank(rank);
        if (idx < 0) {
            return 0;
        }
        String stored = ranks.remove(idx);
        int moved = 0;
        for (GuildMember m : members.values()) {
            if (m.getRank().equalsIgnoreCase(stored)) {
                m.setRank(DEFAULT_RANK);
                moved++;
            }
        }
        return moved;
    }

    /** 0 = master, 1 = highest custom/officer rank, ... Lower number = more power. */
    public int getLevel(String rank) {
        if (MASTER_RANK.equalsIgnoreCase(rank)) {
            return 0;
        }
        int idx = indexOfRank(rank);
        return idx < 0 ? ranks.size() : idx + 1;
    }

    public int getLevel(UUID uuid) {
        GuildMember m = members.get(uuid);
        return m == null ? Integer.MAX_VALUE : getLevel(m.getRank());
    }

    /** The rank one step above the given one, or null if there is none below the master. */
    public String higherRank(String rank) {
        int idx = indexOfRank(rank);
        return idx <= 0 ? null : ranks.get(idx - 1);
    }

    /** The rank one step below the given one, or null if it is already the lowest. */
    public String lowerRank(String rank) {
        int idx = indexOfRank(rank);
        return (idx < 0 || idx >= ranks.size() - 1) ? null : ranks.get(idx + 1);
    }

    // ----------------------------------------------------------------- members

    public Collection<GuildMember> getMembers() {
        return members.values();
    }

    public int size() {
        return members.size();
    }

    public GuildMember getMember(UUID uuid) {
        return members.get(uuid);
    }

    public GuildMember getMemberByName(String name) {
        for (GuildMember m : members.values()) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    public void addMember(GuildMember member) {
        members.put(member.getUuid(), member);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isMaster(UUID uuid) {
        return master != null && master.equals(uuid);
    }

    // ------------------------------------------------------------------ online

    public List<Player> getOnlinePlayers() {
        List<Player> online = new ArrayList<Player>();
        for (UUID uuid : members.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                online.add(p);
            }
        }
        return online;
    }

    public int getOnlineCount() {
        return getOnlinePlayers().size();
    }
}
