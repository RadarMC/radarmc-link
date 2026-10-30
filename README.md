# RadarLink

[Русская версия](#radarlink-по-русски)

RadarLink connects your Minecraft server to [RadarMC](https://radarmc.org):

- **Joins after a copied IP.** Your RadarMC analytics show how many players who copied your IP on the site actually joined.
- **Vote rewards and streak bonuses.** A reward for every vote on RadarMC, plus bonuses when a player's streak of daily votes reaches a set length. Players who are offline get the reward on their next join.
- **Status in the dashboard.** The «Plugin» tab shows that RadarLink is connected, its version and platform.

Works on Spigot, Paper, Folia and Bukkit hybrids from Minecraft 1.7.10 to the newest version, on Velocity and on BungeeCord. Bedrock players are recognised through Floodgate when it is installed.

## Supported platforms

«Tested» means the version passed the real-server matrix (`scripts/matrix/`): RadarLink enabled without errors or warnings, sent a heartbeat with the right platform, reported a bot player's join and gave it a vote reward. «Should work» means RadarLink uses the same API there but was not run separately.

| Platform | Versions | Java | Status |
|---|---|---|---|
| Paper | 1.8.8, 1.16.5, 1.20.4, 1.21.11 (behind BungeeCord), 26.1.2 (behind Velocity), 26.3 | 8, 8, 21, 21, 25, 25 | Tested |
| Spigot | 1.12.2 | 8 | Tested |
| Folia | 26.2 | 25 | Tested |
| Forge + Bukkit hybrids | Crucible 1.7.10 (Cauldron/Thermos line), Mohist 1.20.1 | 8, 21 | Tested |
| Spigot, Paper, CraftBukkit | every version from 1.7.10 to 26.3 | 8 and newer | Should work |
| Folia | 1.19.4 - 26.1 | 17 and newer | Should work |
| Purpur, Pufferfish and other Paper forks | the same versions as Paper | | Should work |
| Other hybrids (Arclight, CatServer, Magma, Thermos) | any that load Bukkit plugins | | Should work |
| Velocity | 4.2.0 (with 1.8.8 and 26.1.2 servers behind it) | 25 | Tested |
| Velocity | 3.1.1 - 4.x | 11 and newer | Should work |
| BungeeCord | latest build (26.1) | 25 | Tested |
| Waterfall | latest build | 8 and newer | Should work |

A server behind a proxy can run any supported version: rewards travel over the `radarlink:reward` channel, a name that is valid on 1.7.10 and on the newest versions alike.

To reach `api.radarmc.org` over HTTPS, a server on Java 8 needs update 8u141 or newer (the Let's Encrypt root certificate). Update old Java 8 builds: the server needs it for its own security too.

## Which file to download

| Server | File |
|---|---|
| Spigot, Paper, Folia, Purpur, Bukkit hybrids | `RadarLink-Bukkit-<version>.jar` |
| Velocity | `RadarLink-Velocity-<version>.jar` |
| BungeeCord, Waterfall | `RadarLink-Bungee-<version>.jar` |
| Not sure | `RadarLink-<version>.jar` works on every platform |

The files are on the «Plugin» tab of your server dashboard, on `https://cdn.radarmc.org/radarlink/` and on the [releases page](https://github.com/RadarMC/radarmc-link/releases). Checksums are in `SHA256SUMS`.

## Installation

1. Put the jar into the `plugins` folder and start the server. `plugins/RadarLink/config.yml` appears.
2. In your server dashboard on radarmc.org open the «API keys» tab and create a key.
3. Put the key (`api-key`) and your server page address (`server-slug`: the last part of `radarmc.org/servers/<slug>`) into `config.yml`.
4. Run `/radarlink reload` (`/radarlinkproxy reload` on a proxy) or restart the server. Within a minute the «Plugin» tab shows «Connected».

## Updating

Replace the jar in `plugins` and restart the server. If the `config.yml` format has changed, RadarLink moves your settings and rewards to the new format itself and keeps the old file next to it (`config.yml.v<version>.bak`). The console lists what changed on start. Leave the `config-version` line alone: it tells RadarLink the file is already up to date.

## Settings (`config.yml`)

| Key | Default | What it does |
|---|---|---|
| `api-url` | `https://api.radarmc.org` | RadarMC API address. No need to change it. |
| `api-key` | empty | The server API key from the «API keys» tab. Without it RadarLink waits and sends nothing. |
| `server-slug` | empty | The last part of your server page address. |
| `send-joins` | `true` | Report player joins (nick, UUID, IP). |
| `behind-proxy` | `false` | The server sits behind a Velocity or BungeeCord proxy that also runs RadarLink. |
| `vote-poll-interval` | `60` | How often to check for new votes, in seconds (30-3600). |
| `offline-rewards` | `true` | Keep the reward of an offline player and give it on join. |
| `proxy-rewards` | `forward` | Proxy only: `forward` sends the reward to the player's server, `commands` runs the commands on the proxy, `off` gives nothing. |
| `proxy-secret` | empty | Shared secret of the proxy and the servers behind it, at least 16 characters. |
| `vote-reward` | empty | Reward for every vote, see below. |
| `streak-rewards` | empty | Bonuses for a vote streak, see below. |

## Vote rewards

Rewards are set only in `config.yml` on your server. The site shows just the thresholds and labels and never receives the commands, so even someone with your API key cannot run commands on your server.

```yaml
vote-reward:
  label: "Diamond for a vote"
  commands:
    - "give %player% diamond 1"

streak-rewards:
  - streak: 7
    label: "VIP for 7 days"
    commands:
      - "lp user %player% parent addtemp vip 7d"
  - streak: 30
    repeat-every: 30
    label: "Legendary key"
    commands:
      - "crate key give %player% legendary"
```

- `vote-reward` runs on **every** vote, whatever the streak.
- `streak-rewards` are bonuses on top of the vote reward. A bonus is given **once**: on the day the streak reaches exactly `streak` days (the same streak rule as on the site). In the example the player gets a diamond every day, VIP on day 7 and a key on day 30.
- `repeat-every` (optional) gives the bonus again every that many days: with `streak: 30` and `repeat-every: 30` the key comes on day 30, 60, 90 and so on. Without `repeat-every` (or with `repeat-every: 0`) the bonus comes only on day 30, until the streak breaks and builds up again.
- If several bonuses fall on the same day (for example `streak: 7` with `repeat-every: 7` and `streak: 14`), all of them are given, lowest `streak` first.
- You can set only `vote-reward` or only `streak-rewards`. A vote with no reward and no bonus is simply confirmed.
- `%player%` is the player's nick, `%streak%` the streak length.
- `label` is an optional short caption for the dashboard.
- Commands run from the console. The nick is checked (Latin letters, digits, `_`, a dot or asterisk at the start for Bedrock), so nothing extra gets into a command.
- If the player is offline and `offline-rewards: true`, the reward is kept in `plugins/RadarLink/rewards.json` and given a few seconds after the next join, together with that day's bonus. With `false` the vote waits on the site for up to 30 days and is given once the player is online.
- Every vote is given once: RadarLink remembers handled votes across restarts. A streak day bonus is given once too: if two accounts vote for the same nick on the same day, the second vote gets only `vote-reward`.
- Bedrock players enter their nick on the site exactly as in game, with the Floodgate prefix (usually a dot: `.Steve`).

If another plugin already gives out RadarMC votes, leave both `vote-reward` and `streak-rewards` unset: RadarLink then leaves votes alone and only reports joins.

## Proxy networks (Velocity, BungeeCord)

1. Install RadarLink on the proxy and on every server behind it. Use the same `api-key` and `server-slug` everywhere.
2. Set `behind-proxy: true` on the servers behind the proxy. Only the proxy then reports joins, so one join to the network is not counted several times.
3. Pick a `proxy-secret` (at least 16 characters) and set the same value on the proxy and on every server. It is not the API key.
4. Rewards are set on the servers (`vote-reward`, `streak-rewards`). The proxy finds the player and forwards the reward over the `radarlink:reward` channel to the server they are playing on. The server gives the reward and the streak bonus and acknowledges it. To run commands in the proxy console instead, set `proxy-rewards: commands` on the proxy and define the rewards there. The proxy decides whether a streak bonus is due for the whole network, so a streak day bonus is never given twice, even if the player moves to another server. Proxies and servers on RadarLink 1.1 and newer work together in any combination. RadarLink 1.0 neither accepts nor forwards rewards through a proxy, so update RadarLink on every server in the network.

Channel messages are signed with `proxy-secret`, and the proxy drops messages players send to this channel, so a reward cannot be forged. Servers behind the proxy can run any supported version, 1.8.8 included.

## What RadarLink sends

| When | What |
|---|---|
| On start and every 5 minutes | Platform and its version, RadarLink version, players online, reward settings (thresholds, `repeat-every`, command counts, labels) |
| On a player join (batched every 30 seconds) | Nick, UUID, IP, Bedrock flag, join time |
| Every minute | A request for new votes and the confirmation of given ones |

The player's IP is needed to match the join with an IP copy on the site. RadarMC turns it into an anonymous code at once, with a key that changes every day, and does not store the IP itself. RadarLink writes IPs neither to disk nor to logs. The server owner sees only the number of joins on the site. To send no joins at all, set `send-joins: false`.

## Commands

| Command | Where | What it does |
|---|---|---|
| `/radarlink status` (`/rlink status`) | Spigot, Paper, Folia | Connection status, join and reward queues |
| `/radarlink reload` | Spigot, Paper, Folia | Reload `config.yml` |
| `/radarlinkproxy status`, `/radarlinkproxy reload` (`/rlinkproxy`) | Velocity, BungeeCord | The same on the proxy |

Permission: `radarlink.admin` (operators by default). The proxy command has its own name so it does not shadow `/radarlink` of the servers behind it.

## Troubleshooting

- **«RadarMC rejected the connection (HTTP 401)».** The key is wrong or revoked. Create a new one on the «API keys» tab.
- **«HTTP 403 API_KEY_WRONG_SERVER» or «HTTP 404».** The key belongs to another server, or `server-slug` has a typo.
- **«network error: SSLHandshakeException» on Java 8.** Update Java 8 to 8u141 or newer.
- **A reward did not arrive.** Check `/radarlink status` and that the nick on the site matches the nick in game. A streak bonus comes only on the day the streak equals `streak` exactly (or a multiple of `repeat-every` after it). On a proxy network check that `proxy-secret` is the same everywhere.
- Questions: [radarmc.org/faq](https://radarmc.org/faq) or the Telegram support bot [@radarmc_bot](https://t.me/radarmc_bot).

## Building

Requirements: JDK 21 to run Gradle, plus a Java 8 runtime for `testJava8` (found locally or downloaded by the Gradle toolchain resolver). The Gradle wrapper downloads Gradle 9.8 (checksum pinned).

```bash
./gradlew check           # format check, unit tests for every module, the core tests again on Java 8
./gradlew spotlessApply   # formats the Java sources (palantir-java-format)
./gradlew pluginJars      # shaded jars in build/plugins/
```

Without a local JDK, run it in Docker:

```bash
docker run --rm -u "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/gradle -v "$HOME/.gradle:/gradle" \
  -v "$PWD:$PWD" -w "$PWD" gradle:jdk21 ./gradlew --no-daemon check pluginJars
```

### Compatibility rules

- Every shipped class is Java 8 bytecode (`options.release = 8`), so one jar runs from Minecraft 1.7.10 (Java 8) to the newest server (Java 25). No records, `var`, switch expressions, text blocks, `List.of`, `String.strip`, `java.net.http` and the like in `core`, `bukkit`, `velocity`, `bungee`. `core:testJava8` runs the core tests on a real Java 8 runtime, so a Java 9+ call fails the build even where `--release 8` cannot see it (reflection, libraries).
- `bukkit` compiles against `spigot-api 1.8.8`, the oldest API published in a Maven repository. Anything that differs is reached by reflection behind runtime checks: Folia's global and entity schedulers (`ServerScheduler.FoliaScheduler`), `Bukkit.getOnlinePlayers()` (an array on early 1.7.10 builds, `BukkitCompat`). Everything else RadarLink calls exists unchanged since 1.7.10. `plugin.yml` has `api-version: "1.13"` (ignored by 1.8-1.12, no legacy mode on 1.13+) and `folia-supported: true`. The manifest carries `paperweight-mappings-namespace: mojang` (no server internals are used, so Paper 1.20.5+ skips remapping).
- `velocity` compiles against `velocity-api 3.1.1` (Java 8 bytecode), the oldest API with everything used; its event and command API is unchanged in Velocity 4.
- `bungee` compiles against `bungeecord-api 1.21-R0.4` (Java 8 bytecode).
- HTTP goes through `HttpURLConnection` (10 s connect, 15 s read, no redirects). Gson and SnakeYAML are shaded and relocated to `org.radarmc.link.lib`, so they never clash with the old copies bundled in 1.8 servers. Floodgate (2.x API and the legacy 1.x API) is reached only through reflection.
- The proxy channel is `radarlink:reward`: namespaced as 1.13+ requires and 16 characters, the 1.7.10 limit (1.8-1.12 allow 20), so one name works everywhere.

### Code style

- One format for every module: palantir-java-format through Spotless, checked by `./gradlew check`.
- No comments in the code: names, small methods and tests carry the meaning. User documentation lives in this README and in the comments of the default `config.yml`.
- Classes are `final` unless built for extension, fields are `final` where possible, dependencies come in through constructors, and absent values are `Optional` or empty collections in public APIs.

### Modules

| Module | Contents |
|---|---|
| `core` | Platform-neutral logic. `api`: HTTP client and payloads. `config`: loading, validation and migration of `config.yml`. `joins`: join batching. `rewards`: reward plan, vote processing, persistent reward store. `proxy`: signed `radarlink:reward` messages. `players`: online players, nick rules, Floodgate. `CoreLifecycle` and `RadarCore`: start, stop, reload and status for every platform. No platform dependencies. |
| `bukkit` | Bukkit/Spigot/Paper/Folia plugin. Tests use MockBukkit on the newest API. |
| `velocity` | Velocity plugin (`velocity-plugin.json` generated by the annotation processor). Data folder `plugins/RadarLink`. |
| `bungee` | BungeeCord/Waterfall plugin. |
| `universal` | One jar with all three platform descriptors. |

### Real-server matrix

`scripts/matrix/` starts real servers in Docker (Crucible 1.7.10, Paper 1.8.8 to the newest, Spigot, Folia, Mohist, Velocity and BungeeCord with backends), a stub API and a headless player, and checks that RadarLink enables cleanly, sends heartbeats, reports the join and gives a vote reward. See `scripts/matrix/README.md`. It is heavy and not part of CI.

### Design notes

- One worker thread per instance does all network I/O; only console commands run on the main thread (or the Folia global region).
- Joins are kept in memory only (at most 1000, sent in batches of 50 every 30 s, dropped after 10 min), so IPs never touch the disk.
- A vote reward is marked handled in `rewards.json` before local commands run (at most once). Forwarded rewards are marked after the backend server acks; the server remembers vote ids, so a retry never pays twice.
- Rewards: `vote-reward` runs on every vote, then every `streak-rewards` entry whose `streak` equals the voter's streak S, or with `repeat-every` R where S > streak and (S - streak) % R == 0, lowest streak first (`RewardPlan`).
- `config.yml` carries `config-version` (absent means 1). On start and on reload `ConfigFiles` runs `ConfigMigrator` before loading: every step from the file's version up rewrites what changed meaning, then the file is rebuilt on the bundled default (its header and comments, the owner's values, the default block for missing settings, unknown settings kept at the end), the old file is copied to `config.yml.v<old>.bak` and the new one is written through a temp file. Step 1 to 2: the `streak: 1` tier of `reward-tiers` becomes `vote-reward` and the other tiers become `streak-rewards` with `repeat-every: 1`. A new format version adds a `ConfigMigrator.Step` (like `RewardTiersStep`) to `STEPS` and bumps `CURRENT_VERSION` and `config-version` in the default config.
- Streak bonuses are claimed per player and streak value in `rewards.json` (`bonuses`: vote time and vote id, kept 35 days): a second vote with the same streak whose site time (`created_at`) is less than 24 hours from the first (two site accounts voting for one nick on the same day) gets only the vote reward; the same vote claims again on a retry. A streak returns to a value only after a gap of at least 25 hours, so this never blocks a real new streak day, and votes from different streak runs keep their bonuses even when they are paid at the same join.
- Behind a proxy the proxy decides the bonus for the whole network (one store, the site's vote time) and sends the decision in the `radarlink:reward` message (version byte 2: the vote, the player, the streak, the vote time and a give/skip flag); the server follows it instead of its own memory. Any other version and trailing bytes are rejected. A reward without an ack within 15 seconds stays pending and is sent again later.
- Reward commands never leave the server. The heartbeat reports only thresholds, `repeat_every`, command counts and optional labels (`vote_reward` and `streak_rewards`).

### Releases

Bump `version` in `gradle.properties`, commit, tag `v<version>` and push the tag. `.github/workflows/release.yml` runs the tests and attaches `RadarLink-Bukkit-<v>.jar`, `RadarLink-Velocity-<v>.jar`, `RadarLink-Bungee-<v>.jar`, `RadarLink-<v>.jar` and `SHA256SUMS` to a GitHub release. Uploading them to `https://cdn.radarmc.org/radarlink/` is a deploy step.

---

# RadarLink по-русски

RadarLink связывает ваш Minecraft-сервер с [RadarMC](https://radarmc.org):

- **«Вошли в игру».** Показывает в аналитике, сколько игроков, скопировавших IP на RadarMC, действительно зашли на сервер.
- **Награды за голоса и бонусы за серию.** Выдаёт награду за каждый голос на RadarMC и бонусы, когда серия дней подряд доходит до заданной длины. Игрокам не в сети награда сохраняется до следующего входа.
- **Статус в кабинете.** На вкладке «Плагин» видно, что RadarLink подключён, его версию и платформу.

Работает на Spigot, Paper, Folia и гибридах Bukkit с Minecraft 1.7.10 до самой новой версии, на Velocity и BungeeCord. Игроков Bedrock видит через Floodgate, если он установлен.

## Поддерживаемые платформы

«Проверено» значит, что версия прошла матрицу на настоящем сервере: RadarLink включился без ошибок и предупреждений, отправил статус с правильной платформой, сообщил о входе игрока-бота и выдал ему награду за голос (`scripts/matrix/`). «Должно работать» значит, что RadarLink использует там тот же API, но отдельно не запускался.

| Платформа | Версии | Java | Статус |
|---|---|---|---|
| Paper | 1.8.8, 1.16.5, 1.20.4, 1.21.11 (за BungeeCord), 26.1.2 (за Velocity), 26.3 | 8, 8, 21, 21, 25, 25 | Проверено |
| Spigot | 1.12.2 | 8 | Проверено |
| Folia | 26.2 | 25 | Проверено |
| Гибриды Forge + Bukkit | Crucible 1.7.10 (линия Cauldron/Thermos), Mohist 1.20.1 | 8, 21 | Проверено |
| Spigot, Paper, CraftBukkit | все версии между 1.7.10 и 26.3 | 8 и новее | Должно работать |
| Folia | 1.19.4 - 26.1 | 17 и новее | Должно работать |
| Purpur, Pufferfish и другие форки Paper | те же версии, что у Paper | | Должно работать |
| Другие гибриды (Arclight, CatServer, Magma, Thermos) | любые, где загружаются плагины Bukkit | | Должно работать |
| Velocity | 4.2.0 (с серверами 1.8.8 и 26.1.2 за ним) | 25 | Проверено |
| Velocity | 3.1.1 - 4.x | 11 и новее | Должно работать |
| BungeeCord | последняя сборка (26.1) | 25 | Проверено |
| Waterfall | последняя сборка | 8 и новее | Должно работать |

Сервер за прокси может быть любой поддерживаемой версии: награды пересылаются по каналу `radarlink:reward`, имя которого допустимо и на 1.7.10, и на новых версиях.

Для связи с `api.radarmc.org` по HTTPS серверу на Java 8 нужно обновление 8u141 или новее (корневой сертификат Let's Encrypt). Старые сборки Java 8 обновите: это нужно и для безопасности самого сервера.

## Какой файл скачать

| Сервер | Файл |
|---|---|
| Spigot, Paper, Folia, Purpur, гибриды Bukkit | `RadarLink-Bukkit-<версия>.jar` |
| Velocity | `RadarLink-Velocity-<версия>.jar` |
| BungeeCord, Waterfall | `RadarLink-Bungee-<версия>.jar` |
| Не уверены | `RadarLink-<версия>.jar` подходит для всех платформ |

Файлы лежат в кабинете сервера на вкладке «Плагин», на `https://cdn.radarmc.org/radarlink/` и на [странице релизов](https://github.com/RadarMC/radarmc-link/releases). Контрольные суммы в `SHA256SUMS`.

## Установка

1. Положите jar-файл в папку `plugins` и запустите сервер. Появится файл `plugins/RadarLink/config.yml`.
2. В кабинете сервера на radarmc.org откройте вкладку «API-ключи» и создайте ключ.
3. Впишите в `config.yml` ключ (`api-key`) и адрес страницы сервера (`server-slug`: последняя часть ссылки `radarmc.org/servers/<slug>`).
4. Выполните `/radarlink reload` (на прокси `/radarlinkproxy reload`) или перезапустите сервер. Через минуту статус на вкладке «Плагин» сменится на «Подключён».

## Обновление

Замените jar-файл в папке `plugins` и перезапустите сервер. Если формат `config.yml` изменился, RadarLink сам переносит в новый формат ваши настройки и награды, а старый файл сохраняет рядом (`config.yml.v<версия>.bak`). Что именно изменилось, написано в консоли при запуске. Строку `config-version` не меняйте: по ней RadarLink понимает, что файл уже обновлён.

## Настройки (`config.yml`)

| Ключ | По умолчанию | Что делает |
|---|---|---|
| `api-url` | `https://api.radarmc.org` | Адрес API RadarMC. Менять не нужно. |
| `api-key` | пусто | Ключ API сервера с вкладки «API-ключи». Без него RadarLink ждёт и ничего не отправляет. |
| `server-slug` | пусто | Последняя часть адреса страницы сервера. |
| `send-joins` | `true` | Сообщать о входах игроков (ник, UUID, IP). |
| `behind-proxy` | `false` | Сервер стоит за Velocity или BungeeCord, на котором тоже есть RadarLink. |
| `vote-poll-interval` | `60` | Как часто проверять новые голоса, в секундах (30-3600). |
| `offline-rewards` | `true` | Сохранить награду игроку не в сети и выдать при входе. |
| `proxy-rewards` | `forward` | Только прокси: `forward` переслать награду на сервер игрока, `commands` выполнить команды на прокси, `off` не выдавать. |
| `proxy-secret` | пусто | Общий секрет прокси и серверов за ним, не короче 16 символов. |
| `vote-reward` | пусто | Награда за каждый голос, см. ниже. |
| `streak-rewards` | пусто | Бонусы за серию голосов, см. ниже. |

## Награды за голоса

Награды настраиваются только в `config.yml` на вашем сервере. Сайт показывает лишь пороги и подписи, текст команд туда не отправляется, поэтому даже с вашим ключом API никто не сможет выполнить команды на сервере.

```yaml
vote-reward:
  label: "Алмаз за голос"
  commands:
    - "give %player% diamond 1"

streak-rewards:
  - streak: 7
    label: "VIP на 7 дней"
    commands:
      - "lp user %player% parent addtemp vip 7d"
  - streak: 30
    repeat-every: 30
    label: "Легендарный ключ"
    commands:
      - "crate key give %player% legendary"
```

- `vote-reward` выполняется на **каждый** голос, какой бы ни была серия.
- `streak-rewards` - бонусы сверх награды за голос. Бонус выдаётся **один раз**: в день, когда серия дошла ровно до `streak` дней (правило серии то же, что на сайте). В примере игрок получает алмаз каждый день, VIP на 7-й день и ключ на 30-й.
- `repeat-every` (по желанию) - выдавать бонус снова каждые столько дней: с `streak: 30` и `repeat-every: 30` ключ приходит на 30-й, 60-й, 90-й день и дальше. Без `repeat-every` (или с `repeat-every: 0`) бонус приходит только на 30-й день, пока серия не прервётся и не наберётся заново.
- Если в один день совпали несколько бонусов (например, `streak: 7` с `repeat-every: 7` и `streak: 14`), выдаются все, начиная с меньшего `streak`.
- Можно задать только `vote-reward` или только `streak-rewards`. Голос без награды и без бонуса просто подтверждается.
- `%player%` - ник игрока, `%streak%` - длина серии.
- `label` - короткая подпись для кабинета, по желанию.
- Команды выполняются от консоли. Ник проверяется (латиница, цифры, `_`, у Bedrock точка или звёздочка в начале), так что в команду не попадёт ничего лишнего.
- Если игрок не в сети и `offline-rewards: true`, награда сохраняется в `plugins/RadarLink/rewards.json` и выдаётся через несколько секунд после входа, вместе с бонусом за тот день. Если `false`, голос ждёт на сайте до 30 дней и выдаётся, когда игрок окажется в сети.
- Каждый голос выдаётся один раз: RadarLink помнит обработанные голоса даже после перезапуска. Бонус за день серии тоже выдаётся один раз: если за один ник в тот же день проголосовали с двух аккаунтов, второй голос получит только `vote-reward`.
- Игроки Bedrock указывают на сайте ник так же, как в игре, с префиксом Floodgate (обычно точка: `.Steve`).

Если голоса RadarMC уже выдаёт другой плагин, не задавайте ни `vote-reward`, ни `streak-rewards`: тогда RadarLink не трогает голоса и только сообщает о входах.

## Сеть с прокси (Velocity, BungeeCord)

1. Поставьте RadarLink на прокси и на каждый сервер за ним. Везде укажите один и тот же `api-key` и `server-slug`.
2. На серверах за прокси включите `behind-proxy: true`. Тогда о входах сообщает только прокси, и один вход в сеть не считается несколько раз.
3. Придумайте `proxy-secret` (не короче 16 символов) и впишите одинаковым на прокси и на всех серверах. Это не ключ API.
4. Награды настраиваются на серверах (`vote-reward`, `streak-rewards`). Прокси находит игрока и пересылает награду на сервер, где он сейчас играет, через канал `radarlink:reward`. Сервер выдаёт награду и бонус за серию и подтверждает получение. Если вместо этого нужно выполнять команды в консоли прокси, укажите на прокси `proxy-rewards: commands` и задайте награды там. Прокси решает, положен ли бонус за серию, сразу для всей сети, поэтому бонус за день серии не выдаётся дважды, даже если игрок перешёл на другой сервер. Прокси и серверы с RadarLink 1.1 и новее работают вместе в любом сочетании. RadarLink 1.0 награды через прокси не принимает и не пересылает, поэтому обновите RadarLink на всех серверах сети.

Сообщения в канале подписаны `proxy-secret`, а сообщения от игроков в этот канал прокси не пропускает, поэтому подделать награду нельзя. Серверы за прокси могут быть любой поддерживаемой версии, в том числе 1.8.8.

## Что RadarLink отправляет

| Когда | Что |
|---|---|
| При запуске и каждые 5 минут | Платформа и её версия, версия RadarLink, сколько игроков в сети, настройки наград (пороги, `repeat-every`, число команд, подписи) |
| При входе игрока (пакетами раз в 30 секунд) | Ник, UUID, IP, признак Bedrock, время входа |
| Раз в минуту | Запрос новых голосов и подтверждение выданных |

IP игрока нужен, чтобы сопоставить вход с копированием IP на сайте. RadarMC сразу превращает его в обезличенный код с ключом, который меняется каждые сутки, и не хранит сам IP. RadarLink не пишет IP ни на диск, ни в логи. Владелец сервера видит на сайте только количество входов. Чтобы не отправлять входы совсем, укажите `send-joins: false`.

## Команды

| Команда | Где | Что делает |
|---|---|---|
| `/radarlink status` (`/rlink status`) | Spigot, Paper, Folia | Статус подключения, очередь входов и наград |
| `/radarlink reload` | Spigot, Paper, Folia | Перечитать `config.yml` |
| `/radarlinkproxy status`, `/radarlinkproxy reload` (`/rlinkproxy`) | Velocity, BungeeCord | То же на прокси |

Право: `radarlink.admin` (по умолчанию у операторов). На прокси команда называется по-другому, чтобы не перекрывать `/radarlink` серверов за ним.

## Если что-то не работает

- **«RadarMC rejected the connection (HTTP 401)».** Ключ неверный или отозван. Создайте новый на вкладке «API-ключи».
- **«HTTP 403 API_KEY_WRONG_SERVER» или «HTTP 404».** Ключ от другого сервера или `server-slug` указан с ошибкой.
- **«network error: SSLHandshakeException» на Java 8.** Обновите Java 8 до 8u141 или новее.
- **Награда не пришла.** Проверьте `/radarlink status` и что ник на сайте совпадает с ником в игре. Бонус за серию приходит только в день, когда серия ровно равна `streak` (или кратна `repeat-every` после него). На сети с прокси проверьте, что `proxy-secret` одинаковый везде.
- Вопросы: [radarmc.org/faq](https://radarmc.org/faq) или бот поддержки в Telegram [@radarmc_bot](https://t.me/radarmc_bot).

Сборка из исходников и устройство плагина описаны выше, в разделе [Building](#building).
