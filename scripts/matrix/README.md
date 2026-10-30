# RadarLink real-server matrix

Starts real Minecraft servers and proxies in Docker and checks RadarLink on each one:

1. RadarLink enables with no warning or error of its own (the expected `api-url uses plain http` warning is ignored, the stub API is plain http).
2. A heartbeat reaches the stub API with the right `platform` and `software`, and every request carries `User-Agent: RadarLink/<version> (<platform>)`.
3. A headless player (`RadarBot`, mineflayer) joins and the join is reported with nick, UUID and IP.
4. The stub offers one pending vote (streak 3). RadarLink queues it while the bot is offline, confirms it, and runs the vote reward and the streak 3 bonus (`say RADARLINK-REWARD RadarBot 3`) when the bot joins. Behind a proxy, the proxy forwards the reward over `radarlink:reward` and the backend runs it.
5. On shutdown RadarLink sends a `stopping` heartbeat (reported, not required).

It is heavy (a server, sometimes a proxy, the stub and the bot) and runs one scenario at a time. It is not part of CI.

## Run

```bash
./gradlew pluginJars               # or the Docker command from the main README
scripts/matrix/run.sh list         # scenario names
scripts/matrix/run.sh              # every scenario
scripts/matrix/run.sh paper-1.8.8 velocity-newest
```

Needs Docker and `python3`. Images: `itzg/minecraft-server:java8|java21|java25`, `itzg/mc-proxy:java25`, `node:22.23.3-alpine3.24`. Server jars, ViaVersion and mineflayer are downloaded on the first run and kept in `scripts/matrix/.work/` (gitignored; `WORK_DIR` moves it). Results: `.work/results/<time>/summary.md`, one JSON file per scenario, the server, proxy and bot logs and every API request (`requests.jsonl`).

Containers are named `rl-api`, `rl-mc`, `rl-proxy`, `rl-bot` on the network `rl-matrix`; the script removes them on exit.

## Scenarios

| Scenario | Server | Java | Bot | Jar |
|---|---|---|---|---|
| `crucible-1.7.10` | Crucible 1.7.10 (Forge + Bukkit hybrid, Cauldron/Thermos line) | 8 | 1.7.10 (raw protocol + FML handshake) | Bukkit |
| `paper-1.8.8` | Paper 1.8.8 | 8 | 1.8.8 | Bukkit |
| `spigot-1.12.2` | Spigot 1.12.2 | 8 | 1.12.2 | universal |
| `paper-1.16.5` | Paper 1.16.5 | 8 | 1.16.5 | Bukkit |
| `paper-1.20.4` | Paper 1.20.4 | 21 | 1.20.4 | Bukkit |
| `mohist-1.20.1` | Mohist 1.20.1 (Forge + Bukkit hybrid) | 21 | 1.20.1 | Bukkit |
| `paper-newest` | Paper `PAPER_NEWEST` (26.3) + ViaVersion/ViaBackwards | 25 | `BOT_NEWEST` (26.1) | Bukkit |
| `folia-newest` | Folia `FOLIA_NEWEST` (26.2) + ViaVersion/ViaBackwards | 25 | 26.1 | Bukkit |
| `velocity-1.8.8` | Velocity `VELOCITY_VERSION` (4.2.0) + Paper 1.8.8 backend | 25 / 8 | 1.8.8 | Velocity + Bukkit |
| `velocity-newest` | Velocity 4.2.0 + Paper `BACKEND_NEWEST` (26.1.2) backend | 25 / 25 | 26.1 | universal on both |
| `bungeecord` | BungeeCord (latest) + Paper 1.21.11 backend | 25 / 21 | 1.21.11 | Bungee + Bukkit |

Backends use legacy (BungeeCord) forwarding, which works from 1.8 to the newest version. ViaVersion is only there because the bot (mineflayer 4.39) speaks up to 26.1; bump `BOT_NEWEST` when mineflayer catches up. Behind Velocity the backend is 26.1.2 without Via: Via kicks the bot for its spawn move (`invalid_player_movement`) before the proxy forwards the reward, which says nothing about RadarLink. The bot does not move (`physicsEnabled: false`). For a proxy scenario the check also reads the proxy's `rewards.json`: the forwarded reward must be completed, which happens only after the backend's signed ack.

1.7.x is not in mineflayer, so the bot logs in with minecraft-protocol directly and does the Forge 1.7.10 `FML|HS` handshake itself (`fml17Handshake` in `bot/bot.js`). Spigot 1.7.10 itself is no longer downloadable anywhere the image looks.

Override versions with environment variables, for example `PAPER_NEWEST=26.4 FOLIA_NEWEST=26.3 scripts/matrix/run.sh paper-newest folia-newest`. A full run takes about an hour (longer on the first run, which downloads every server).

## Files

| File | What |
|---|---|
| `run.sh` | Scenarios, containers, configs |
| `stub-api/server.js` | Stub of the four RadarMC endpoints, logs requests |
| `bot/bot.js` | Player that joins, prints chat and leaves after 45 s (mineflayer, or minecraft-protocol for 1.7.10) |
| `check.py` | Pass/fail per server from the logs and `requests.jsonl` |
| `proxy_config.py` | Points the generated Velocity/BungeeCord config at the backend |
| `summary.py` | Markdown table of a results directory |
