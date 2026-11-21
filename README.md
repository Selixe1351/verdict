# Verdict

Bans, mutes, kicks and warnings for Paper, synced across every server on your network. Simple commands, MongoDB storage, optional Redis for instant sync.

Runs on Paper 1.21 and newer, Java 21+.

## Install

1. Drop `Verdict-<version>.jar` into `plugins/` and start the server once.
2. Set your MongoDB connection in `plugins/Verdict/config.yml`.
3. On a network, set the same `redis.uri` on every server so bans, kicks and staff alerts reach all of them instantly.
4. Restart.

## Commands

```
/ban <player> [duration] [reason]
/tempban <player> <duration> [reason]
/mute <player> [duration] [reason]
/tempmute <player> <duration> [reason]
/kick <player> [reason]
/warn <player> [reason]
/unban <player>
/unmute <player>
/ipban <address|player> [duration] [reason]
/tempipban <address|player> <duration> [reason]
/unbanip <address|player>
/history <player>
/alts <player>
```

Durations look like `30m`, `7d` or `1d12h`. Leave the duration out, or write `perm`, for a permanent punishment.

`/history` opens a menu of the player's punishments, newest first.

## Permissions

| Permission | Allows |
|---|---|
| `verdict.ban` | ban and tempban |
| `verdict.mute` | mute and tempmute |
| `verdict.kick` | kick |
| `verdict.warn` | warn |
| `verdict.unban` | unban |
| `verdict.unmute` | unmute |
| `verdict.history` | view history |
| `verdict.alts` | view alts |
| `verdict.ipban` | ban and unban IP addresses |
| `verdict.notify` | see every punishment as it happens, and alerts when a banned player's alt joins |

All default to operators.

## How it works

- Banned players are refused at login, by account or by IP.
- Muted players cannot chat or use the commands listed under `muted-commands`.
- Every login records the player's name and IP, which powers name lookups and alt alerts.
- When a player joins who shares an IP with a banned account, staff with `verdict.notify` are alerted.
- Punishments are stored in MongoDB. Reads are cached, and with Redis every server learns about a change immediately.

## Messages

Every message is in `config.yml` under `messages`, in [MiniMessage](https://docs.advntr.dev/minimessage/format.html) format. A message can be one line, or a list of lines. Placeholders: `<player>`, `<staff>`, `<reason>`, `<type>`, `<duration>`, `<expires>` and `<prefix>`.

## API

Add the `api` module as a `compileOnly` dependency and `depend: [Verdict]` to your `plugin.yml`:

```kotlin
val verdict = Bukkit.getServicesManager().load(VerdictAPI::class.java)!!

verdict.getActiveBan(uuid)
verdict.getHistory(uuid)
verdict.punish(PunishmentType.MUTE, uuid, "Spam", 1.hours)
```

Calls are blocking, so run them off the main thread.

## Building

```
./gradlew build
```

The plugin jar is `bukkit/build/libs/Verdict-<version>.jar`. Tests use Testcontainers and are skipped when Docker is not running.

Built with [Conduit](https://github.com/Selixe1351/conduit) and [Directive](https://github.com/Selixe1351/directive).

## License

[MIT](LICENSE)
