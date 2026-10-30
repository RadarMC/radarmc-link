#!/usr/bin/env bash
# RadarLink real-server matrix. Runs one scenario at a time (one Minecraft container set), see README.md.
# usage: scripts/matrix/run.sh [scenario...]      no arguments: every scenario; "list" prints them
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../.." && pwd)
VERSION=$(grep '^version=' "$REPO/gradle.properties" | cut -d= -f2)
JARS=${JAR_DIR:-$REPO/build/plugins}
WORK=${WORK_DIR:-$HERE/.work}
RESULTS=${RESULTS_DIR:-$WORK/results/$(date +%Y%m%d-%H%M%S)}
NODE_IMAGE=${NODE_IMAGE:-node:22.23.3-alpine3.24}
SERVER_IMAGE=${SERVER_IMAGE:-itzg/minecraft-server}
PROXY_IMAGE=${PROXY_IMAGE:-itzg/mc-proxy:java25}
VELOCITY_VERSION=${VELOCITY_VERSION:-4.2.0}
PAPER_NEWEST=${PAPER_NEWEST:-26.3}
FOLIA_NEWEST=${FOLIA_NEWEST:-26.2}
# Newest protocol the bot (mineflayer 4.39) speaks; newer servers get ViaVersion + ViaBackwards.
BOT_NEWEST=${BOT_NEWEST:-26.1}
BACKEND_NEWEST=${BACKEND_NEWEST:-26.1.2}
NET=rl-matrix
KEY=rmc_Matrix0123456789abcdefghijKLMNOP
SLUG=matrix-test
SECRET=matrix-proxy-secret-0123
VIA="viaversion,viabackwards"
UIDGID="$(id -u):$(id -g)"

SCENARIOS=(crucible-1.7.10 paper-1.8.8 spigot-1.12.2 paper-1.16.5 paper-1.20.4 mohist-1.20.1 paper-newest folia-newest
  velocity-1.8.8 velocity-newest bungeecord)

log() { printf '[matrix] %s\n' "$*"; }

cleanup() {
  docker rm -f rl-api rl-mc rl-proxy rl-bot >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# config.yml for RadarLink. $1 dir, $2 behind-proxy (true/false), $3 proxy (true/false)
write_config() {
  local dir=$1 behind=$2 proxy=$3
  rm -rf "$dir/plugins/RadarLink" "$dir/plugins/radarlink"
  mkdir -p "$dir/plugins/RadarLink"
  {
    echo "api-url: \"http://rl-api:8080\""
    echo "api-key: \"$KEY\""
    echo "server-slug: \"$SLUG\""
    echo "behind-proxy: $behind"
    echo "vote-poll-interval: 30"
    echo "proxy-secret: \"$SECRET\""
    echo "config-version: 2"
    if [ "$proxy" = true ]; then
      echo "proxy-rewards: forward"
    else
      echo "vote-reward:"
      echo "  label: \"Matrix vote\""
      echo "  commands: [\"say RADARLINK-VOTE %player% %streak%\"]"
      echo "streak-rewards:"
      echo "  - streak: 3"
      echo "    label: \"Matrix streak 3\""
      echo "    commands: [\"say RADARLINK-REWARD %player% %streak%\"]"
    fi
  } > "$dir/plugins/RadarLink/config.yml"
}

# $1 data dir, $2 jar (Bukkit, Velocity, Bungee or universal)
install_jar() {
  rm -f "$1"/plugins/RadarLink*.jar
  cp "$JARS/$2" "$1/plugins/"
}

wait_log() { # $1 container, $2 regex, $3 timeout seconds, $4 only log lines since this time (optional)
  local i since=()
  [ -n "${4:-}" ] && since=(--since "$4")
  for ((i = 0; i < $3; i += 3)); do
    if docker logs "${since[@]}" "$1" 2>&1 | grep -Eq "$2"; then return 0; fi
    if [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" != true ]; then
      log "$1 stopped while waiting for /$2/"; return 1
    fi
    sleep 3
  done
  log "timeout waiting for /$2/ in $1"; return 1
}

start_stub() {
  docker network create "$NET" >/dev/null
  : > "$OUT/requests.jsonl"
  docker run -d --name rl-api --network "$NET" -u "$UIDGID" -e API_KEY="$KEY" -e LOG_FILE=/out/requests.jsonl \
    -v "$HERE/stub-api:/app:ro" -v "$OUT:/out" "$NODE_IMAGE" node /app/server.js >/dev/null
}

# $1 container name, $2 data dir, $3 java tag, $4 TYPE, $5 VERSION, $6 extra plugins (Modrinth slugs or "")
start_server() {
  local name=$1 dir=$2 tag=$3 type=$4 version=$5 extra=$6
  local env=(-e EULA=TRUE -e TYPE="$type" -e VERSION="$version" -e ONLINE_MODE=FALSE -e INIT_MEMORY=512M
    -e MAX_MEMORY=1G -e VIEW_DISTANCE=4 -e SIMULATION_DISTANCE=4 -e SPAWN_PROTECTION=0 -e DIFFICULTY=peaceful
    -e ENABLE_RCON=FALSE -e UID="$(id -u)" -e GID="$(id -g)")
  if [ -n "$extra" ]; then env+=(-e MODRINTH_PROJECTS="$extra"); fi
  docker run -d --name "$name" --network "$NET" "${env[@]}" -v "$dir:/data" "$SERVER_IMAGE:$tag" >/dev/null
  wait_log "$name" 'Done \(' 900
}

# Bukkit servers behind a proxy use legacy (BungeeCord) forwarding: works from 1.8 to the newest version.
enable_bungeecord_mode() {
  local f=$1/spigot.yml
  if [ -f "$f" ]; then sed -i 's/^\(  *\)bungeecord: false/\1bungeecord: true/' "$f"; else printf 'settings:\n  bungeecord: true\n' > "$f"; fi
}

# $1 container, $2 dir, $3 TYPE (VELOCITY or BUNGEECORD)
start_proxy() {
  local name=$1 dir=$2 type=$3
  local env=(--user "$UIDGID" -e TYPE="$type" -e MEMORY=512m)
  [ "$type" = VELOCITY ] && env+=(-e VELOCITY_VERSION="$VELOCITY_VERSION")
  local ready='Done \(|Listening on'
  local config=$dir/velocity.toml
  [ "$type" = BUNGEECORD ] && config=$dir/config.yml
  if [ ! -f "$config" ]; then
    log "first start of $type to generate its config"
    mkdir -p "$dir/plugins"
    docker run -d --name "$name" --network "$NET" "${env[@]}" -v "$dir:/server" "$PROXY_IMAGE" >/dev/null
    wait_log "$name" "$ready" 600 || return 1
    docker stop -t 30 "$name" >/dev/null; docker rm "$name" >/dev/null
  fi
  python3 "$HERE/proxy_config.py" "$type" "$config" rl-mc:25565
  docker run -d --name "$name" --network "$NET" "${env[@]}" -v "$dir:/server" "$PROXY_IMAGE" >/dev/null
  wait_log "$name" "$ready" 600
}

run_bot() { # $1 host, $2 port, $3 version
  mkdir -p "$WORK/bot-modules" "$WORK/npm-cache"
  docker run --rm --name rl-bot --network "$NET" -u "$UIDGID" -e HOME=/tmp -e npm_config_cache=/npm-cache \
    -v "$HERE/bot:/bot" -v "$WORK/bot-modules:/bot/node_modules" -v "$WORK/npm-cache:/npm-cache" -w /bot "$NODE_IMAGE" \
    sh -c "test -d node_modules/mineflayer || npm install --no-audit --no-fund --no-package-lock >/dev/null; node bot.js $1 $2 $3 45" \
    > "$OUT/$SCENARIO-bot.log" 2>&1
  tail -n 3 "$OUT/$SCENARIO-bot.log" | sed 's/^/[bot] /'
}

# Re-runs check.py on fresh logs until it passes or $1 seconds pass. $2.. check.py arguments.
wait_checks() {
  local limit=$1 i c; shift
  for ((i = 0; i < limit; i += 5)); do
    for c in rl-mc rl-proxy; do
      docker inspect "$c" >/dev/null 2>&1 && docker logs "$c" > "$OUT/$SCENARIO-$c.log" 2>&1
    done
    python3 "$HERE/check.py" "$OUT" "$SCENARIO" --quiet "$@" && return 0
    sleep 5
  done
  return 1
}

stop_and_collect() { # containers...
  local c
  for c in "$@"; do docker stop -t 60 "$c" >/dev/null 2>&1; done
  for c in "$@"; do docker logs "$c" > "$OUT/$SCENARIO-$c.log" 2>&1; docker rm -f "$c" >/dev/null 2>&1; done
}

# One standalone server: $1 java tag, $2 TYPE, $3 VERSION, $4 bot version, $5 jar, $6 extra plugins
standalone() {
  local tag=$1 type=$2 version=$3 botv=$4 jar=$5 extra=${6:-}
  local dir=$WORK/$SCENARIO/server
  mkdir -p "$dir/plugins"
  write_config "$dir" false false
  install_jar "$dir" "$jar"
  start_stub
  start_server rl-mc "$dir" "$tag" "$type" "$version" "$extra" || { stop_and_collect rl-mc rl-api; return 1; }
  local spec="$type-$version:$OUT/$SCENARIO-rl-mc.log:${tag#java}:server"
  run_bot rl-mc 25565 "$botv"
  wait_checks 90 --server "$spec" || true
  stop_and_collect rl-mc rl-api
  python3 "$HERE/check.py" "$OUT" "$SCENARIO" --server "$spec"
}

# Proxy plus one backend: $1 proxy TYPE, $2 proxy jar, $3 backend java tag, $4 backend TYPE, $5 VERSION,
# $6 bot version, $7 backend jar, $8 extra backend plugins
proxied() {
  local ptype=$1 pjar=$2 tag=$3 type=$4 version=$5 botv=$6 jar=$7 extra=${8:-}
  local pdir=$WORK/$SCENARIO/proxy dir=$WORK/$SCENARIO/backend
  mkdir -p "$dir/plugins" "$pdir/plugins"
  write_config "$dir" true false
  install_jar "$dir" "$jar"
  enable_bungeecord_mode "$dir"
  write_config "$pdir" false true
  install_jar "$pdir" "$pjar"
  start_stub
  start_server rl-mc "$dir" "$tag" "$type" "$version" "$extra" || { stop_and_collect rl-mc rl-api; return 1; }
  enable_bungeecord_mode "$dir"
  if ! grep -q 'bungeecord: true' "$dir/spigot.yml"; then log "spigot.yml has no bungeecord setting"; fi
  local restarted
  restarted=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  docker restart rl-mc >/dev/null
  wait_log rl-mc 'Done \(' 600 "$restarted" || { stop_and_collect rl-mc rl-api; return 1; }
  start_proxy rl-proxy "$pdir" "$ptype" || { stop_and_collect rl-proxy rl-mc rl-api; return 1; }
  local checks=(--reward-in "$type-$version" --proxy-store "$pdir/plugins/RadarLink/rewards.json"
    --server "$ptype:$OUT/$SCENARIO-rl-proxy.log:25:proxy"
    --server "$type-$version:$OUT/$SCENARIO-rl-mc.log:${tag#java}:backend")
  run_bot rl-proxy 25577 "$botv"
  wait_checks 120 "${checks[@]}" || true
  stop_and_collect rl-proxy rl-mc rl-api
  python3 "$HERE/check.py" "$OUT" "$SCENARIO" "${checks[@]}"
}

run_scenario() {
  case "$1" in
    paper-1.8.8) standalone java8 PAPER 1.8.8 1.8.8 "RadarLink-Bukkit-$VERSION.jar" ;;
    spigot-1.12.2) standalone java8 SPIGOT 1.12.2 1.12.2 "RadarLink-$VERSION.jar" ;;
    paper-1.16.5) standalone java8 PAPER 1.16.5 1.16.5 "RadarLink-Bukkit-$VERSION.jar" ;;
    paper-1.20.4) standalone java21 PAPER 1.20.4 1.20.4 "RadarLink-Bukkit-$VERSION.jar" ;;
    paper-newest) standalone java25 PAPER "$PAPER_NEWEST" "$BOT_NEWEST" "RadarLink-Bukkit-$VERSION.jar" "$VIA" ;;
    folia-newest) standalone java25 FOLIA "$FOLIA_NEWEST" "$BOT_NEWEST" "RadarLink-Bukkit-$VERSION.jar" "$VIA" ;;
    velocity-1.8.8) proxied VELOCITY "RadarLink-Velocity-$VERSION.jar" java8 PAPER 1.8.8 1.8.8 "RadarLink-Bukkit-$VERSION.jar" ;;
    # The backend is the newest version the bot speaks without ViaVersion: Via kicks the bot for its spawn move
    # before the proxy forwards the reward. Paper 26.3 and Folia 26.2 themselves are covered above.
    velocity-newest) proxied VELOCITY "RadarLink-$VERSION.jar" java25 PAPER "$BACKEND_NEWEST" "$BOT_NEWEST" "RadarLink-$VERSION.jar" ;;
    bungeecord) proxied BUNGEECORD "RadarLink-Bungee-$VERSION.jar" java21 PAPER 1.21.11 1.21.11 "RadarLink-Bukkit-$VERSION.jar" ;;
    # Forge/Bukkit hybrids. Crucible (a Thermos/Cauldron fork) is the only 1.7.10 Bukkit server still downloadable.
    mohist-1.20.1) standalone java21 MOHIST 1.20.1 1.20.1 "RadarLink-Bukkit-$VERSION.jar" ;;
    crucible-1.7.10) standalone java8 CRUCIBLE 1.7.10 1.7.10 "RadarLink-Bukkit-$VERSION.jar" ;;
    *) log "unknown scenario $1"; return 2 ;;
  esac
}

if [ "${1:-}" = list ]; then printf '%s\n' "${SCENARIOS[@]}"; exit 0; fi
[ -f "$JARS/RadarLink-$VERSION.jar" ] || { log "no jars in $JARS: run ./gradlew pluginJars first"; exit 2; }
mkdir -p "$RESULTS"
OUT=$RESULTS
failed=()
for SCENARIO in "${@:-${SCENARIOS[@]}}"; do
  log "=== $SCENARIO"
  cleanup
  if ! run_scenario "$SCENARIO"; then failed+=("$SCENARIO"); fi
  cleanup
done
python3 "$HERE/summary.py" "$RESULTS" > "$RESULTS/summary.md" && cat "$RESULTS/summary.md"
if [ ${#failed[@]} -gt 0 ]; then log "FAILED: ${failed[*]}"; exit 1; fi
log "all scenarios passed; results in $RESULTS"
