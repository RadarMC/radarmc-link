package org.radarmc.link.core.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.radarmc.link.core.MutableClock;
import org.radarmc.link.core.PlatformAdapter;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.RecordingLogger;
import org.radarmc.link.core.ServerSnapshot;
import org.radarmc.link.core.players.FloodgateHook;
import org.radarmc.link.core.players.OnlinePlayers;
import org.radarmc.link.core.rewards.BonusDecision;
import org.radarmc.link.core.rewards.BonusGuard;
import org.radarmc.link.core.rewards.LocalDelivery;
import org.radarmc.link.core.rewards.Reward;
import org.radarmc.link.core.rewards.RewardPlan;
import org.radarmc.link.core.rewards.RewardStore;
import org.radarmc.link.core.rewards.StreakReward;
import org.radarmc.link.core.rewards.VoteReward;

class ForwardingAndReceiverTest {
    private static final String SECRET = "0123456789abcdef";
    private static final byte[] KEY = RewardMessage.secretBytes(SECRET);
    private static final Instant DAY7 = Instant.parse("2026-10-05T10:00:00Z");
    private static final RewardPlan SPLIT = RewardPlan.split(
            new VoteReward(Arrays.asList("give %player% diamond 1"), null),
            Arrays.asList(new StreakReward(7, 0, Arrays.asList("lp user %player% parent addtemp vip 7d"), "VIP")));

    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T12:00:00Z"));
    private final RecordingLogger log = new RecordingLogger();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final List<String> commands = new ArrayList<>();
    private final OnlinePlayers proxyOnline = new OnlinePlayers(() -> FloodgateHook.NO_PREFIX);
    private final List<byte[]> toServer = new ArrayList<>();
    private boolean sendOk = true;

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void proxyAndServerCompleteARewardOnce() throws Exception {
        RewardReceiver receiver = receiver("lobby", diamonds());
        ForwardingDelivery forwarding = forwarding(BonusGuard.NONE, ForwardingDelivery.ACK_TIMEOUT);
        proxyOnline.joined("Steve");

        assertEquals(Optional.of("Steve"), forwarding.onlineName("steve"));
        CompletableFuture<Boolean> done = forwarding.deliver("Steve", new Reward("v1", "steve", 7, clock.instant()));
        assertEquals(1, toServer.size());

        Optional<byte[]> ack = receiver.receive("Steve", toServer.get(0));
        assertTrue(ack.isPresent());
        assertTrue(forwarding.onServerMessage(ack.get()));
        assertTrue(done.get(1, TimeUnit.SECONDS));
        assertEquals(Arrays.asList("give Steve diamond 7"), commands);

        assertTrue(receiver.receive("Steve", toServer.get(0)).isPresent(), "a repeat is acked");
        assertEquals(1, commands.size(), "but not paid again");
        assertEquals(0, forwarding.waitingForAck());
    }

    @Test
    void receiverRejectsForeignOrForgedMessages() {
        RewardReceiver receiver = receiver("lobby", diamonds());
        byte[] forAlex = reward("v1", "Alex").encode(KEY);
        assertFalse(receiver.receive("Steve", forAlex).isPresent());
        byte[] forged = reward("v2", "Steve").encode(RewardMessage.secretBytes("wrong-secret-0000"));
        assertFalse(receiver.receive("Steve", forged).isPresent());
        byte[] ack = reward("v3", "Steve").ack(clock.instant()).encode(KEY);
        assertFalse(receiver.receive("Steve", ack).isPresent());
        assertTrue(commands.isEmpty());
        assertEquals(1, log.lines.size(), "warned once");
    }

    @Test
    void playerWithoutServerFailsAtOnce() throws Exception {
        ForwardingDelivery forwarding =
                new ForwardingDelivery((player, msg) -> false, proxyOnline, SECRET, BonusGuard.NONE, clock, scheduler);
        assertFalse(forwarding
                .deliver("Steve", new Reward("v1", "Steve", 1, clock.instant()))
                .get(1, TimeUnit.SECONDS));
    }

    @Test
    void unansweredRewardFailsAfterTheTimeout() throws Exception {
        ForwardingDelivery forwarding = forwarding(BonusGuard.NONE, Duration.ofMillis(50));
        assertFalse(forwarding
                .deliver("Steve", new Reward("v1", "Steve", 1, clock.instant()))
                .get(2, TimeUnit.SECONDS));
        assertEquals(0, forwarding.waitingForAck());
    }

    @Test
    void proxyIgnoresInvalidAcks() {
        ForwardingDelivery forwarding = forwarding(BonusGuard.NONE, ForwardingDelivery.ACK_TIMEOUT);
        CompletableFuture<Boolean> done = forwarding.deliver("Steve", new Reward("v1", "Steve", 1, clock.instant()));
        assertFalse(forwarding.onServerMessage(reward("v1", "Steve").encode(KEY)));
        assertFalse(forwarding.onServerMessage(new byte[64]));
        assertFalse(done.isDone());
        assertEquals(1, forwarding.waitingForAck());
    }

    @Test
    void proxyDecidesTheStreakBonusOnceForAllServers() {
        RewardStore proxyStore = RewardStore.load(dir.resolve("proxy"), clock, log);
        ForwardingDelivery forwarding = forwarding(proxyStore, ForwardingDelivery.ACK_TIMEOUT);
        RewardReceiver lobby = receiver("lobby", SPLIT);
        RewardReceiver survival = receiver("survival", SPLIT);

        forwarding.deliver("Steve", new Reward("v7a", "Steve", 7, clock.instant(), DAY7));
        assertEquals(BonusDecision.GIVE, decode(toServer.get(0)).bonus());
        lobby.receive("Steve", toServer.get(0));
        clock.advance(Duration.ofHours(30));
        forwarding.deliver("Steve", new Reward("v7b", "steve", 7, clock.instant(), DAY7.plus(Duration.ofHours(3))));
        survival.receive("Steve", toServer.get(1));

        assertEquals(
                BonusDecision.SKIP,
                decode(toServer.get(1)).bonus(),
                "same streak day by site time, other server, 30 h later");
        assertEquals(
                Arrays.asList("give Steve diamond 1", "lp user Steve parent addtemp vip 7d", "give Steve diamond 1"),
                commands);
    }

    @Test
    void retriedForwardKeepsItsBonusAndAnotherStreakRunGetsOneToo() throws Exception {
        RewardStore proxyStore = RewardStore.load(dir.resolve("proxy"), clock, log);
        ForwardingDelivery forwarding = forwarding(proxyStore, ForwardingDelivery.ACK_TIMEOUT);
        sendOk = false;
        assertFalse(forwarding
                .deliver("Steve", new Reward("v7", "Steve", 7, clock.instant(), DAY7))
                .get(1, TimeUnit.SECONDS));
        sendOk = true;
        forwarding.deliver("Steve", new Reward("v7", "Steve", 7, clock.instant(), DAY7));
        forwarding.deliver("Steve", new Reward("old7", "Steve", 7, clock.instant(), DAY7.minus(Duration.ofDays(10))));
        assertEquals(BonusDecision.GIVE, decode(toServer.get(0)).bonus());
        assertEquals(BonusDecision.GIVE, decode(toServer.get(1)).bonus(), "the retry of the same vote");
        assertEquals(BonusDecision.GIVE, decode(toServer.get(2)).bonus(), "a vote from an earlier streak run");
        assertEquals(DAY7, decode(toServer.get(1)).votedAt());
    }

    private ForwardingDelivery forwarding(BonusGuard guard, Duration ackTimeout) {
        ForwardingDelivery.Transport transport = (player, message) -> {
            toServer.add(message);
            return sendOk;
        };
        return new ForwardingDelivery(transport, proxyOnline, SECRET, guard, clock, scheduler, ackTimeout);
    }

    private static RewardPlan diamonds() {
        return RewardPlan.split(
                new VoteReward(Arrays.asList("give %player% diamond %streak%"), null),
                Collections.<StreakReward>emptyList());
    }

    private RewardMessage reward(String voteId, String player) {
        return RewardMessage.reward(voteId, player, 1, clock.instant(), clock.instant(), false);
    }

    private RewardReceiver receiver(String server, RewardPlan plan) {
        try {
            Files.createDirectories(dir.resolve(server));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        RewardStore store = RewardStore.load(dir.resolve(server), clock, log);
        LocalDelivery local = new LocalDelivery(
                new RecordingPlatform(), new OnlinePlayers(() -> FloodgateHook.NO_PREFIX), () -> plan, store);
        return new RewardReceiver(SECRET, store, local, clock, log);
    }

    private RewardMessage decode(byte[] data) {
        return RewardMessage.decode(data, KEY, clock).get();
    }

    private final class RecordingPlatform implements PlatformAdapter {
        @Override
        public RadarLogger logger() {
            return log;
        }

        @Override
        public Path dataDirectory() {
            return dir;
        }

        @Override
        public ServerSnapshot snapshot() {
            return new ServerSnapshot("paper", "Paper 1.21.4", 0, 20);
        }

        @Override
        public void runOnMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void dispatchConsoleCommand(String command) {
            commands.add(command);
        }
    }
}
