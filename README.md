# Guilds

Hypixel-style guild plugin for Spigot / Paper 1.8.8 with custom ranks, guild chat and PlaceholderAPI support.

## Getting the jar (no commands needed)

1. Create an empty repository on GitHub and upload / push the contents of this folder.
2. Open the **Actions** tab. The **Build** workflow runs automatically on every push.
3. Open the finished run and download **Guilds-jar** from *Artifacts*. Put `Guilds.jar` in your server's `plugins/` folder.

### Publishing a release
Push a tag such as `v1.0.0` (or run the **Release** workflow from the Actions tab and type a tag).
The jar is attached to a GitHub Release automatically.

## Commands
`/g` and `/guild`: `create`, `disband`, `invite`, `join`, `chat`, `createrank`, `deleterank`, `promote`, `demote`, `leave`, `kick`, `list`, `info`, `transfer`, `help`. `/gc <message>` sends guild chat.

## Placeholders
`%guild_name%`, `%guild_tag%`, `%guild_rank%`, `%guild_master%`, `%guild_members%`, `%guild_online%`, `%guild_max_members%`, `%guild_in_guild%`, `%guild_is_master%`

## Permissions
`guild.use` (default true), `guild.create` (default true), `guild.chat.color` (op), `guild.admin` (op)

## Java version
Compiles with JDK 17 by default. If your 1.8.8 server runs on Java 8, set `maven.compiler.release` to `8` in `pom.xml` (the code is Java 8 compatible).
