package org.radarmc.link.core;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.radarmc.link.core.api.ApiClient;
import org.radarmc.link.core.api.Heartbeat;
import org.radarmc.link.core.api.JoinEntry;
import org.radarmc.link.core.api.StreakRewardSummary;
import org.radarmc.link.core.api.VoteRewardSummary;
import org.radarmc.link.core.config.ProxyRewards;
import org.radarmc.link.core.config.RadarConfig;
import org.radarmc.link.core.joins.JoinBatcher;
import org.radarmc.link.core.players.FloodgateHook;
import org.radarmc.link.core.players.OnlinePlayers;
import org.radarmc.link.core.proxy.ForwardingDelivery;
import org.radarmc.link.core.proxy.RewardReceiver;
import org.radarmc.link.core.rewards.BonusGuard;
import org.radarmc.link.core.rewards.LocalDelivery;
import org.radarmc.link.core.rewards.RewardDelivery;
import org.radarmc.link.core.rewards.RewardPlan;
import org.radarmc.link.core.rewards.RewardStore;
import org.radarmc.link.core.rewards.StreakReward;
import org.radarmc.link.core.rewards.VoteProcessor;

public final class RadarCore {
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMinutes(5);
    private static final Duration JOIN_FLUSH_INTERVAL = Duration.ofSeconds(30);
    private static final Duration VOTE_POLL_START_DELAY = Duration.ofSeconds(5);
    private static final Duration PRUNE_START_DELAY = Duration.ofMinutes(1);
    private static final Duration PRUNE_INTERVAL = Duration.ofHours(1);
    private static final Duration READY_DELAY = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration STOP_WAIT = Duration.ofSeconds(5);

    private final PlatformAdapter platform;
    private final RadarConfig config;
    private final Role role;
    private final FloodgateHook floodgate;
    private final Clock clock = Clock.systemUTC();
    private final RadarLogger log;
    private final ScheduledExecutorService worker;
    private final String instanceId;
    private final OnlinePlayers online;
    private final RewardMode rewardMode;
    private final HeartbeatSender heartbeats;
    private final JoinBatcher joins;
    private final RewardStore store;
    private final VoteProcessor votes;
    private final ForwardingDelivery forwarding;
    private final RewardReceiver receiver;

    private RadarCore(Builder builder) {
        this.platform = builder.platform;
        this.config = builder.config;
        this.role = builder.role;
        this.floodgate = builder.floodgate;
        this.log = platform.logger();
        this.worker = Executors.newSingleThreadScheduledExecutor(RadarCore::workerThread);
        this.instanceId = InstanceId.load(platform.dataDirectory(), log);
        this.online = new OnlinePlayers(floodgate::prefix);
        this.rewardMode = resolveRewardMode(config, role, builder.transport != null);
        ApiClient api =
                new ApiClient(config.apiUrl(), config.apiKey(), config.serverSlug(), userAgent(), REQUEST_TIMEOUT);
        this.heartbeats = new HeartbeatSender(api, this::heartbeat, clock, log);
        this.joins =
                reportsJoins() ? new JoinBatcher(batch -> api.sendJoins(instanceId, batch), worker, clock, log) : null;
        this.store = rewardMode == RewardMode.OFF ? null : RewardStore.load(platform.dataDirectory(), clock, log);
        LocalDelivery local =
                new LocalDelivery(platform, online, config::rewards, store == null ? BonusGuard.NONE : store);
        this.forwarding = rewardMode == RewardMode.FORWARD
                ? new ForwardingDelivery(builder.transport, online, config.proxySecret(), store, clock, worker)
                : null;
        this.receiver = rewardMode == RewardMode.RECEIVE
                ? new RewardReceiver(config.proxySecret(), store, local, clock, log)
                : null;
        RewardDelivery delivery = forwarding != null ? forwarding : local;
        this.votes = rewardMode.pollsVotes()
                ? new VoteProcessor(
                        api, store, delivery, config.offlineRewards(), worker, clock, log, floodgate::prefix)
                : null;
    }

    public static Builder builder(PlatformAdapter platform, RadarConfig config, Role role) {
        return new Builder(platform, config, role);
    }

    static RewardMode resolveRewardMode(RadarConfig config, Role role, boolean canForward) {
        switch (role) {
            case SERVER:
                return config.rewards().isEmpty() ? RewardMode.OFF : RewardMode.LOCAL;
            case SERVER_BEHIND_PROXY:
                return config.hasProxySecret() ? RewardMode.RECEIVE : RewardMode.OFF;
            case PROXY:
            default:
                return proxyRewardMode(config, canForward);
        }
    }

    private static RewardMode proxyRewardMode(RadarConfig config, boolean canForward) {
        if (config.proxyRewards() == ProxyRewards.FORWARD && canForward && config.hasProxySecret()) {
            return RewardMode.FORWARD;
        }
        if (config.proxyRewards() == ProxyRewards.COMMANDS && !config.rewards().isEmpty()) {
            return RewardMode.PROXY;
        }
        return RewardMode.OFF;
    }

    public void start() {
        explainRewardMode();
        repeat(() -> heartbeats.send(false), Duration.ZERO, HEARTBEAT_INTERVAL);
        if (joins != null) {
            repeat(guard("join flush", joins::flush), JOIN_FLUSH_INTERVAL, JOIN_FLUSH_INTERVAL);
        }
        if (votes != null) {
            repeat(guard("vote poll", votes::poll), VOTE_POLL_START_DELAY, config.votePollInterval());
        }
        if (store != null) {
            repeat(guard("reward store prune", store::prune), PRUNE_START_DELAY, PRUNE_INTERVAL);
        }
        log.info("RadarLink " + BuildInfo.VERSION + " started for " + config.serverSlug() + " (" + role.description()
                + ")");
    }

    public void stop() {
        worker.execute(() -> {
            if (joins != null) {
                joins.flush();
            }
            heartbeats.send(true);
        });
        worker.shutdown();
        try {
            if (!worker.awaitTermination(STOP_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void onJoin(UUID uuid, String name, InetAddress address) {
        online.joined(name);
        if (joins != null && address != null) {
            joins.add(new JoinEntry(uuid, name, ipString(address), floodgate.isBedrock(uuid), clock.instant()));
        }
        if (votes != null && rewardMode != RewardMode.FORWARD) {
            scheduleReady(name);
        }
    }

    public void onServerConnected(String name) {
        if (votes != null && rewardMode == RewardMode.FORWARD) {
            scheduleReady(name);
        }
    }

    public void onQuit(String name) {
        online.left(name);
    }

    public OnlinePlayers online() {
        return online;
    }

    public Optional<RewardReceiver> receiver() {
        return Optional.ofNullable(receiver);
    }

    public Optional<ForwardingDelivery> forwarding() {
        return Optional.ofNullable(forwarding);
    }

    public RewardMode rewardMode() {
        return rewardMode;
    }

    public List<String> status() {
        List<String> lines = new ArrayList<>();
        lines.add(
                "RadarLink " + BuildInfo.VERSION + ", server " + config.serverSlug() + " (" + role.description() + ")");
        lines.add(heartbeats.status());
        lines.add("Joins: "
                + (joins == null ? "not reported here" : joins.pending() + " waiting, " + joins.sentCount() + " sent"));
        lines.add("Rewards: " + rewardMode.wireName() + ", " + config.rewards().describe()
                + (store == null ? "" : ", " + store.queuedCount() + " queued for offline players"));
        lines.add("Floodgate: " + (floodgate.available() ? "found" : "not found"));
        return lines;
    }

    private Heartbeat heartbeat(boolean stopping) {
        ServerSnapshot snapshot = platform.snapshot();
        RewardPlan plan = rewardMode.runsCommands() ? config.rewards() : RewardPlan.none();
        return new Heartbeat(
                instanceId,
                snapshot.platform(),
                snapshot.software(),
                BuildInfo.VERSION,
                snapshot.onlinePlayers(),
                snapshot.maxPlayers(),
                role == Role.SERVER_BEHIND_PROXY,
                joins != null,
                floodgate.available(),
                rewardMode.wireName(),
                config.offlineRewards(),
                plan.voteReward()
                        .map(vote -> new VoteRewardSummary(vote.commands().size(), vote.label()))
                        .orElse(null),
                streakRewardSummaries(plan),
                stopping);
    }

    private static List<StreakRewardSummary> streakRewardSummaries(RewardPlan plan) {
        List<StreakRewardSummary> summaries = new ArrayList<>();
        for (StreakReward bonus : plan.streakRewards()) {
            summaries.add(new StreakRewardSummary(
                    bonus.streak(), bonus.repeatEvery(), bonus.commands().size(), bonus.label()));
        }
        return summaries;
    }

    private boolean reportsJoins() {
        return config.sendJoins() && role != Role.SERVER_BEHIND_PROXY;
    }

    private void scheduleReady(String name) {
        worker.schedule(
                guard("queued rewards", () -> votes.playerReady(name)), READY_DELAY.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void repeat(Runnable task, Duration initialDelay, Duration interval) {
        worker.scheduleWithFixedDelay(task, initialDelay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private Runnable guard(String name, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("RadarLink " + name + " failed", e);
            }
        };
    }

    private void explainRewardMode() {
        if (role == Role.SERVER_BEHIND_PROXY
                && rewardMode == RewardMode.OFF
                && !config.rewards().isEmpty()) {
            log.warn("Rewards are set but proxy-secret is empty: rewards forwarded by the proxy are ignored. Use the"
                    + " same proxy-secret as on the proxy.");
        }
        if (role == Role.PROXY && config.proxyRewards() == ProxyRewards.FORWARD && rewardMode == RewardMode.OFF) {
            log.warn("proxy-rewards is forward but proxy-secret is empty or too short: vote rewards are off.");
        }
    }

    private String userAgent() {
        return "RadarLink/" + BuildInfo.VERSION + " (" + platform.snapshot().platform() + ")";
    }

    private static Thread workerThread(Runnable task) {
        Thread thread = new Thread(task, "RadarLink-worker");
        thread.setDaemon(true);
        return thread;
    }

    static String ipString(InetAddress address) {
        String ip = address.getHostAddress();
        int scope = ip.indexOf('%');
        return address instanceof Inet6Address && scope >= 0 ? ip.substring(0, scope) : ip;
    }

    public static final class Builder {
        private final PlatformAdapter platform;
        private final RadarConfig config;
        private final Role role;
        private FloodgateHook floodgate = FloodgateHook.absent();
        private ForwardingDelivery.Transport transport;

        private Builder(PlatformAdapter platform, RadarConfig config, Role role) {
            this.platform = platform;
            this.config = config;
            this.role = role;
        }

        public Builder floodgate(FloodgateHook hook) {
            this.floodgate = hook;
            return this;
        }

        public Builder transport(ForwardingDelivery.Transport value) {
            this.transport = value;
            return this;
        }

        public RadarCore build() {
            return new RadarCore(this);
        }
    }
}
