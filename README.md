# GuildPlugin (Spigot / Paper 1.8.8)

Hypixel-style guilds: custom ranks, guild chat, color GUI (&0-&f), tab settings GUI,
PlaceholderAPI expansion, `guilds.yml` storage.

## Automatic build (no manual commands)
1. Create a new GitHub repository and upload / push all files of this project.
2. Open the **Actions** tab. The *Build GuildPlugin* workflow runs automatically on every push.
3. Open the finished run and download the **GuildPlugin** artifact (contains `GuildPlugin.jar`).
4. Drop the jar into your server's `plugins/` folder.

### Make a downloadable Release
Create a tag and push it - the workflow publishes the jar under **Releases**:
```
git tag v1.0.0
git push origin v1.0.0
```
(Or on GitHub: Releases -> Draft a new release -> create tag `v1.0.0`.)

You can also start a build any time via Actions -> Build GuildPlugin -> **Run workflow**.

## Placeholders
`%guild_name%` `%guild_name_colored%` `%guild_rank%` `%guild_color%` `%guild_color_code%`
`%guild_prefix%` `%guild_tab%` `%guild_master%` `%guild_members%` `%guild_online%` `%guild_has%`

## Commands
`/g create|disband|invite|join|accept|chat|createrank|deleterank|ranks|promote|demote|leave|kick|list|info|transfer|color|tab|help`, `/gc <message>`
