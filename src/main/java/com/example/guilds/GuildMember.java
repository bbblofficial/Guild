package com.example.guilds;

import java.util.UUID;

/** A single member entry inside a {@link Guild}. */
public class GuildMember {

    private final UUID uuid;
    private String name;
    private String rank;
    private final long joined;

    public GuildMember(UUID uuid, String name, String rank, long joined) {
        this.uuid = uuid;
        this.name = name;
        this.rank = rank;
        this.joined = joined;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public long getJoined() {
        return joined;
    }
}
