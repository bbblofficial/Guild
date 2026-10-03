package com.example.guilds;

import java.util.*;

/** Data model of a single guild. */
public class Guild {

    public static final String MASTER_RANK = "Guild Master";
    public static final String OFFICER = "Officer";
    public static final String MEMBER = "Member";

    private final String name;
    private UUID master;
    private char color = 'a';
    private TabMode tabMode = TabMode.NAME;
    private final long created;

    /** member -> rank name (the master has MASTER_RANK) */
    private final Map<UUID, String> memberRanks = new LinkedHashMap<>();
    /** member -> last known name (so offline members can be listed / managed) */
    private final Map<UUID, String> memberNames = new HashMap<>();
    /** rank hierarchy, highest -> lowest, WITHOUT the Guild Master */
    private final List<String> ranks = new ArrayList<>();

    public Guild(String name, UUID master, String masterName, long created) {
        this.name = name;
        this.master = master;
        this.created = created;
        ranks.add(OFFICER);
        ranks.add(MEMBER);
        memberRanks.put(master, MASTER_RANK);
        memberNames.put(master, masterName);
    }

    // ---------------------------------------------------------------- basic

    public String getName() { return name; }
    public UUID getMaster() { return master; }
    public boolean isMaster(UUID u) { return master.equals(u); }
    public long getCreated() { return created; }

    public char getColor() { return color; }
    public void setColor(char c) { this.color = Character.toLowerCase(c); }

    public TabMode getTabMode() { return tabMode; }
    public void setTabMode(TabMode m) { this.tabMode = m; }

    // -------------------------------------------------------------- members

    public Set<UUID> getMembers() { return Collections.unmodifiableSet(memberRanks.keySet()); }
    public int size() { return memberRanks.size(); }
    public boolean isMember(UUID u) { return memberRanks.containsKey(u); }

    public void addMember(UUID u, String playerName, String rank) {
        if (!MASTER_RANK.equals(rank) && !ranks.contains(rank)) rank = MEMBER;
        memberRanks.put(u, rank);
        memberNames.put(u, playerName);
    }

    public void removeMember(UUID u) {
        memberRanks.remove(u);
        memberNames.remove(u);
    }

    public String getMemberName(UUID u) {
        String n = memberNames.get(u);
        return n == null ? u.toString().substring(0, 8) : n;
    }

    public void setMemberName(UUID u, String n) { memberNames.put(u, n); }

    public UUID findMember(String playerName) {
        for (Map.Entry<UUID, String> e : memberNames.entrySet()) {
            if (e.getValue().equalsIgnoreCase(playerName) && memberRanks.containsKey(e.getKey())) return e.getKey();
        }
        return null;
    }

    // ---------------------------------------------------------------- ranks

    public String getRank(UUID u) {
        String r = memberRanks.get(u);
        return r == null ? MEMBER : r;
    }

    public void setRank(UUID u, String rank) { memberRanks.put(u, rank); }

    /** -1 for the master, 0 = highest normal rank ... size-1 = lowest rank. */
    public int rankIndex(UUID u) {
        String r = getRank(u);
        if (MASTER_RANK.equals(r)) return -1;
        int i = ranks.indexOf(r);
        return i < 0 ? ranks.size() - 1 : i;
    }

    public List<String> getRanks() { return Collections.unmodifiableList(ranks); }

    public void setRanks(List<String> list) {
        ranks.clear();
        ranks.addAll(list);
        if (!ranks.contains(OFFICER)) ranks.add(0, OFFICER);
        if (!ranks.contains(MEMBER)) ranks.add(MEMBER);
    }

    /** Returns the canonical rank name (case-insensitive lookup) or null. */
    public String findRank(String n) {
        for (String r : ranks) if (r.equalsIgnoreCase(n)) return r;
        if (MASTER_RANK.equalsIgnoreCase(n)) return MASTER_RANK;
        return null;
    }

    public static boolean isDefaultRank(String r) {
        return MASTER_RANK.equals(r) || OFFICER.equals(r) || MEMBER.equals(r);
    }

    /** New ranks are inserted directly above "Member". */
    public void addRank(String n) {
        int idx = ranks.indexOf(MEMBER);
        if (idx < 0) idx = ranks.size();
        ranks.add(idx, n);
    }

    public void removeRank(String n) {
        ranks.remove(n);
        for (Map.Entry<UUID, String> e : memberRanks.entrySet()) {
            if (e.getValue().equals(n)) e.setValue(MEMBER);
        }
    }

    public void transferMaster(UUID newMaster) {
        memberRanks.put(master, OFFICER);
        master = newMaster;
        memberRanks.put(newMaster, MASTER_RANK);
    }
}
