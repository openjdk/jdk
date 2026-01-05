/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.internal.net.http.quic;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.TimeLine;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.quic.QuicTLSEngine;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static jdk.internal.net.http.quic.TerminationCause.forSilentTermination;

/**
 * Keeps track of activity on a {@code QuicConnectionImpl} and manages
 * the idle timeout of the QUIC connection
 */
final class IdleTimeoutManager {

    private static final long NO_IDLE_TIMEOUT = 0;

    private final QuicConnectionImpl connection;
    private final Logger debug;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final AtomicLong idleTimeoutDurationMs = new AtomicLong();
    private final ReentrantLock stateLock = new ReentrantLock();
    // must be accessed only when holding stateLock
    private IdleTimeoutEvent idleTimeoutEvent;
    // must be accessed only when holding stateLock
    private StreamDataBlockedEvent streamDataBlockedEvent;
    // the time (in nanos) at which the last outgoing packet was sent or an
    // incoming packet processed on the connection
    private volatile long lastPacketActivityNanos;

    private final ReentrantLock idleTerminationLock = new ReentrantLock();
    // true if it has been decided to terminate the connection due to being idle,
    // false otherwise. should be accessed only when holding the idleTerminationLock
    private boolean chosenForIdleTermination;
    // the time (in nanos) at which the connection was last reserved for use.
    // should be accessed only when holding the idleTerminationLock
    private long lastUsageReservationNanos;

    private final AtomicReference<Supplier<Boolean>> trafficGenerationCheck = new AtomicReference<>();
    // must be accessed only when holding stateLock
    private PingEvent pingEvent;

    IdleTimeoutManager(final QuicConnectionImpl connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.debug = connection.debug;
    }

    /**
     * Starts the idle timeout management for the connection. This should be called
     * after the handshake is complete for the connection.
     *
     * @throws IllegalStateException if handshake hasn't yet completed or if the handshake
     *                               has failed for the connection
     */
    void start() {
        // start idle management only for successfully completed handshake
        requireSuccessfulHandshake();
        if (shutdown.get()) {
            return;
        }
        this.stateLock.lock();
        try {
            if (shutdown.get()) {
                return;
            }
            startIdleTerminationTimer();
            startStreamDataBlockedTimer();
        } finally {
            this.stateLock.unlock();
        }
    }

    private void requireSuccessfulHandshake() {
        final CompletableFuture<QuicTLSEngine.HandshakeState> handshakeCF =
                this.connection.handshakeFlow().handshakeCF();
        if (!handshakeCF.isDone()) {
            throw new IllegalStateException("handshake isn't yet complete," +
                    " cannot use idle connection management");
        }
        if (handshakeCF.isCompletedExceptionally()) {
            throw new IllegalStateException("cannot use idle connection management for a failed"
                    + " connection");
        }
    }

    private void startIdleTerminationTimer() {
        assert stateLock.isHeldByCurrentThread() : "not holding state lock";
        final Optional<Long> idleTimeoutMillis = getIdleTimeout();
        if (idleTimeoutMillis.isEmpty()) {
            if (debug.on()) {
                debug.log("idle connection management disabled for connection");
            } else {
                Log.logQuic("{0} idle connection management disabled for connection",
                        connection.logTag());
            }
            return;
        }
        final QuicTimerQueue timerQueue = connection.endpoint().timer();
        final Deadline deadline = timeLine().instant().plusMillis(idleTimeoutMillis.get());
        // we don't expect idle timeout management to be started more than once
        assert this.idleTimeoutEvent == null : "idle timeout management"
                + " already started for connection";
        // create the idle timeout event and register with the QuicTimerQueue.
        this.idleTimeoutEvent = new IdleTimeoutEvent(deadline);
        timerQueue.offer(this.idleTimeoutEvent);
        if (debug.on()) {
            debug.log("started QUIC idle timeout management for connection,"
                    + " idle timeout event: " + this.idleTimeoutEvent
                    + " deadline: " + deadline);
        } else {
            Log.logQuic("{0} started QUIC idle timeout management for connection,"
                            + " idle timeout event: {1} deadline: {2}",
                    connection.logTag(), this.idleTimeoutEvent, deadline);
        }
    }

    private void stopIdleTerminationTimer() {
        assert stateLock.isHeldByCurrentThread() : "not holding state lock";
        if (this.idleTimeoutEvent == null) {
            return;
        }
        final QuicEndpoint endpoint = this.connection.endpoint();
        assert endpoint != null : "QUIC endpoint is null";
        // disable the idle timeout timer event
        disableTimedEvent(endpoint.timer(), this.idleTimeoutEvent);
        this.idleTimeoutEvent = null;
    }

    private void startStreamDataBlockedTimer() {
        assert stateLock.isHeldByCurrentThread() : "not holding state lock";
        // 75% of QUIC idle timeout or if QUIC idle timeout is not configured, then 30 seconds
        final long timeoutMillis = getIdleTimeout()
                .map((v) -> (long) (0.75 * v))
                .orElse(30000L);
        final QuicTimerQueue timerQueue = connection.endpoint().timer();
        final Deadline deadline = timeLine().instant().plusMillis(timeoutMillis);
        // we don't expect the timer to be started more than once
        assert this.streamDataBlockedEvent == null : "STREAM_DATA_BLOCKED timer already started";
        // create the timeout event and register with the QuicTimerQueue.
        this.streamDataBlockedEvent = new StreamDataBlockedEvent(deadline, timeoutMillis);
        timerQueue.offer(this.streamDataBlockedEvent);
        if (debug.on()) {
            debug.log("started STREAM_DATA_BLOCKED timer for connection,"
                    + " event: " + this.streamDataBlockedEvent
                    + " deadline: " + deadline);
        } else {
            Log.logQuic("{0} started STREAM_DATA_BLOCKED timer for connection,"
                            + " event: {1} deadline: {2}",
                    connection.logTag(), this.streamDataBlockedEvent, deadline);
        }
    }

    private void stopStreamDataBlockedTimer() {
        assert stateLock.isHeldByCurrentThread() : "not holding state lock";
        if (this.streamDataBlockedEvent == null) {
            return;
        }
        final QuicEndpoint endpoint = this.connection.endpoint();
        assert endpoint != null : "QUIC endpoint is null";
        // disable the stream data blocked timer event
        disableTimedEvent(endpoint.timer(), this.streamDataBlockedEvent);
        this.streamDataBlockedEvent = null;
    }

    // set up a PING timer if the application layer's max idle duration for the connection
    // is larger than that of the negotiated QUIC idle timeout for that connection
    void appLayerMaxIdle(final Duration maxIdle, final Supplier<Boolean> trafficGenerationCheck) {
        Objects.requireNonNull(maxIdle, "maxIdle");
        Objects.requireNonNull(trafficGenerationCheck, "trafficGenerationCheck");
        if (maxIdle.isZero() || maxIdle.isNegative()) {
            throw new IllegalArgumentException("invalid maxIdle duration: " + maxIdle);
        }
        // the application layer must not configure its max idle duration
        // until the QUIC connection's handshake has successfully completed
        requireSuccessfulHandshake();

        if (!this.trafficGenerationCheck.compareAndSet(null, trafficGenerationCheck)) {
            throw new IllegalStateException("app layer max inactivity already set");
        }
        final Optional<Long> quicIdleTimeout = getIdleTimeout();
        if (quicIdleTimeout.isEmpty()) {
            // the QUIC connection will never idle timeout, nothing more to do
            return;
        }
        // we start the PING sending timer event only if the QUIC layer idle timeout
        // is lesser than the app layer's desired idle time
        if (Duration.ofMillis(quicIdleTimeout.get()).compareTo(maxIdle) < 0) {
            this.stateLock.lock();
            try {
                if (shutdown.get()) {
                    return;
                }
                // QUIC connection has a lower idle timeout than the app layer. start a timer
                // which checks with the app layer at regular intervals to decide whether to
                // send a PING to keep the QUIC connection active.
                startPingTimer();
            } finally {
                this.stateLock.unlock();
            }
        }
    }

    private void startPingTimer() {
        assert stateLock.isHeldByCurrentThread() : "not holding state lock";
        // we don't expect the timer to be started more than once
        assert this.pingEvent == null : "PING timer already started";
        final Optional<Long> quicIdleTimeout = getIdleTimeout();
        assert quicIdleTimeout.isPresent() : "QUIC idle timeout is disabled, no need to start PING timer";
        // potential PING generation every 75% of QUIC idle timeout
        final long pingFrequencyMillis = (long) (0.75 * quicIdleTimeout.get());
        assert pingFrequencyMillis > 0 : "unexpected ping frequency: " + pingFrequencyMillis;
        final QuicTimerQueue timerQueue = connection.endpoint().timer();
        final Deadline deadline = timeLine().instant().plusMillis(pingFrequencyMillis);
        // create the timeout event and register with the QuicTimerQueue.
        this.pingEvent = new PingEvent(deadline, pingFrequencyMillis);
        timerQueue.offer(this.pingEvent);
        if (debug.on()) {
            debug.log("started periodic PING for connection,"
                    + " ping event: " + this.pingEvent
                    + " deadline: " + deadline);
        } else {
            Log.logQuic("{0} started periodic PING for connection,"
                            + " ping event: {1} deadline: {2}",
                    connection.logTag(), this.pingEvent, deadline);
        }
    }

    private void stopPingTimer() {
        assert stateLock.isHeldByCurrentThread() : "not holding state lock";
        if (this.pingEvent == null) {
            return;
        }
        final QuicEndpoint endpoint = this.connection.endpoint();
        assert endpoint != null : "QUIC endpoint is null";
        // disable the ping timer event
        disableTimedEvent(endpoint.timer(), this.pingEvent);
        this.pingEvent = null;
        this.trafficGenerationCheck.set(null);
    }


    /**
     * Attempts to notify the idle connection management that this connection should
     * be considered "in use". This way the idle connection management doesn't close
     * this connection during the time the connection is handed out from the pool and any
     * new stream created on that connection.
     *
     * @return true if the connection has been successfully reserved. false
     * otherwise; in which case the connection must not be handed out from the pool.
     */
    boolean tryReserveForUse() {
        this.idleTerminationLock.lock();
        try {
            if (chosenForIdleTermination) {
                // idle termination has been decided for this connection, don't use it
                return false;
            }
            // if the connection is nearing idle timeout due to lack of traffic then
            // don't use it
            final long lastPktActivity = lastPacketActivityNanos;
            final long currentNanos = System.nanoTime();
            final long inactivityMs = MILLISECONDS.convert((currentNanos - lastPktActivity),
                    NANOSECONDS);
            final boolean nearingIdleTimeout = getIdleTimeout()
                    .map((timeoutMillis) -> inactivityMs >= (0.8 * timeoutMillis)) // 80% of idle timeout
                    .orElse(false);
            if (nearingIdleTimeout) {
                return false;
            }
            // express interest in using the connection
            this.lastUsageReservationNanos = System.nanoTime();
            return true;
        } finally {
            this.idleTerminationLock.unlock();
        }
    }


    /**
     * Returns the idle timeout duration, in milliseconds, negotiated for the connection represented
     * by this {@code IdleTimeoutManager}. The negotiated idle timeout of a connection
     * is the minimum of the idle connection timeout that is advertised by the
     * endpoint represented by this {@code IdleTimeoutManager} and the idle
     * connection timeout advertised by the peer. If neither endpoints have advertised
     * any idle connection timeout then this method returns an
     * {@linkplain Optional#empty() empty} value.
     *
     * @return the idle timeout in milliseconds or {@linkplain Optional#empty() empty}
     */
    Optional<Long> getIdleTimeout() {
        final long val = this.idleTimeoutDurationMs.get();
        return val == NO_IDLE_TIMEOUT ? Optional.empty() : Optional.of(val);
    }

    // consider the connection as active as of this moment
    void markActive() {
        lastPacketActivityNanos = System.nanoTime(); // TODO: timeline().instant()?
    }

    void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            // already shutdown
            return;
        }
        this.stateLock.lock();
        try {
            // unregister the timeout events from the QuicTimerQueue
            stopIdleTerminationTimer();
            stopStreamDataBlockedTimer();
            stopPingTimer();
        } finally {
            this.stateLock.unlock();
        }
        if (debug.on()) {
            debug.log("idle timeout manager shutdown");
        }
    }

    void localIdleTimeout(final long timeoutMillis) {
        checkUpdateIdleTimeout(timeoutMillis);
    }

    void peerIdleTimeout(final long timeoutMillis) {
        checkUpdateIdleTimeout(timeoutMillis);
    }

    private void checkUpdateIdleTimeout(final long newIdleTimeoutMillis) {
        if (newIdleTimeoutMillis <= 0) {
            // idle timeout should be non-zero value, we disregard other values
            return;
        }
        long current;
        boolean updated = false;
        // update the idle timeout if the new timeout is lesser
        // than the previously set value
        while ((current = this.idleTimeoutDurationMs.get()) == NO_IDLE_TIMEOUT
                || current > newIdleTimeoutMillis) {
            updated = this.idleTimeoutDurationMs.compareAndSet(current, newIdleTimeoutMillis);
            if (updated) {
                break;
            }
        }
        if (!updated) {
            return;
        }
        if (debug.on()) {
            debug.log("idle connection timeout updated to "
                    + newIdleTimeoutMillis + " milli seconds");
        } else {
            Log.logQuic("{0} idle connection timeout updated to {1} milli seconds",
                    connection.logTag(), newIdleTimeoutMillis);
        }
    }

    private TimeLine timeLine() {
        return this.connection.endpoint().timeSource();
    }

    private static void disableTimedEvent(final QuicTimerQueue timer, final TimedEvent te) {
        // disable the event (refreshDeadline() of TimedEvent will return Deadline.MAX)
        final Deadline nextDeadline = te.nextDeadline;
        if (!nextDeadline.equals(Deadline.MAX)) {
            te.nextDeadline = Deadline.MAX;
            timer.reschedule(te, Deadline.MIN);
        }
    }

    // called when the connection has been idle past its idle timeout duration
    private void idleTimedOut() {
        if (shutdown.get()) {
            return; // nothing to do - the idle timeout manager has been shutdown
        }
        final Optional<Long> timeoutVal = getIdleTimeout();
        assert timeoutVal.isPresent() : "unexpectedly idle timing" +
                " out connection, when no idle timeout is configured";
        final long timeoutMillis = timeoutVal.get();
        if (Log.quic() || debug.on()) {
            // log idle timeout, with packet space statistics
            final String msg = "silently terminating connection due to idle timeout ("
                    + timeoutMillis + " milli seconds)";
            StringBuilder sb = new StringBuilder();
            for (PacketNumberSpace sp : PacketNumberSpace.values()) {
                if (sp == PacketNumberSpace.NONE) continue;
                if (connection.packetNumberSpaces().get(sp) instanceof PacketSpaceManager m) {
                    sb.append("\n  PacketSpace: ").append(sp).append('\n');
                    m.debugState("    ", sb);
                }
            }
            if (Log.quic()) {
                Log.logQuic("{0} {1}: {2}", connection.logTag(), msg, sb.toString());
            } else if (debug.on()) {
                debug.log("%s: %s", msg, sb);
            }
        }
        // silently close the connection and discard all its state
        var type = connection.isClientConnection() ? "client" : "server";
        var label = "quic:" + connection.uniqueId();
        final TerminationCause cause = forSilentTermination(type + " connection idle timed out ("
                + timeoutMillis + " milli seconds) on " + label);
        connection.terminator.terminate(cause);
    }

    private long computeInactivityMillis() {
        assert idleTerminationLock.isHeldByCurrentThread() : "not holding idle termination lock";
        final long currentNanos = System.nanoTime();
        final long lastActiveNanos = Math.max(lastPacketActivityNanos, lastUsageReservationNanos);
        return MILLISECONDS.convert((currentNanos - lastActiveNanos), NANOSECONDS);
    }

    sealed abstract class TimedEvent implements QuicTimedEvent {
        protected final long eventId;
        protected volatile Deadline deadline;
        protected volatile Deadline nextDeadline;

        private TimedEvent(final Deadline deadline) {
            assert deadline != null : "timeout deadline is null";
            this.deadline = this.nextDeadline = deadline;
            this.eventId = QuicTimerQueue.newEventId();
        }

        @Override
        public final Deadline deadline() {
            return this.deadline;
        }

        @Override
        public final Deadline refreshDeadline() {
            if (shutdown.get()) {
                return this.deadline = this.nextDeadline = Deadline.MAX;
            }
            return this.deadline = this.nextDeadline;
        }

        @Override
        public final long eventId() {
            return this.eventId;
        }

        @Override
        public abstract Deadline handle();
    }

    final class IdleTimeoutEvent extends TimedEvent {

        private IdleTimeoutEvent(final Deadline deadline) {
            super(deadline);
        }

        @Override
        public Deadline handle() {
            if (shutdown.get()) {
                // timeout manager is shutdown, nothing more to do
                return this.nextDeadline = Deadline.MAX;
            }
            final Optional<Long> idleTimeout = getIdleTimeout();
            if (idleTimeout.isEmpty()) {
                // nothing to do, don't reschedule
                return Deadline.MAX;
            }
            final long idleTimeoutMillis = idleTimeout.get();
            // check whether the connection has indeed been idle for the idle timeout duration
            idleTerminationLock.lock();
            try {
                Deadline postponed = maybePostponeDeadline(idleTimeoutMillis);
                if (postponed != null) {
                    // not idle long enough, reschedule
                    this.nextDeadline = postponed;
                    return postponed;
                }
                chosenForIdleTermination = true;
            } finally {
                idleTerminationLock.unlock();
            }
            // the connection has been idle for the idle timeout duration, go
            // ahead and terminate it.
            terminateNow();
            assert shutdown.get() : "idle timeout manager was expected to be shutdown";
            this.nextDeadline = Deadline.MAX;
            return Deadline.MAX;
        }

        private Deadline maybePostponeDeadline(final long expectedIdleDurationMs) {
            assert idleTerminationLock.isHeldByCurrentThread() : "not holding idle termination lock";
            final long inactivityMs = computeInactivityMillis();
            if (inactivityMs >= expectedIdleDurationMs) {
                // the connection has been idle long enough, don't postpone the timeout.
                return null;
            }
            // not idle long enough, compute the deadline when it's expected to reach
            // idle timeout
            final long remainingMs = expectedIdleDurationMs - inactivityMs;
            final Deadline next = timeLine().instant().plusMillis(remainingMs);
            if (debug.on()) {
                debug.log("postponing timeout event: " + this + " to fire" +
                        " in " + remainingMs + " milli seconds, deadline: " + next);
            }
            return next;
        }

        private void terminateNow() {
            try {
                idleTimedOut();
            } finally {
                shutdown();
            }
        }

        @Override
        public String toString() {
            return "QuicIdleTimeoutEvent-" + this.eventId;
        }
    }

    final class StreamDataBlockedEvent extends TimedEvent {
        private final long timeoutMillis;

        private StreamDataBlockedEvent(final Deadline deadline, final long timeoutMillis) {
            super(deadline);
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public Deadline handle() {
            if (shutdown.get()) {
                // timeout manager is shutdown, nothing more to do
                return this.nextDeadline = Deadline.MAX;
            }
            // check whether the connection has indeed been idle for the idle timeout duration
            idleTerminationLock.lock();
            try {
                if (chosenForIdleTermination) {
                    // connection is already chosen for termination, no need to send
                    // a STREAM_DATA_BLOCKED
                    this.nextDeadline = Deadline.MAX;
                    return this.nextDeadline;
                }
                final long inactivityMs = computeInactivityMillis();
                if (inactivityMs >= timeoutMillis && connection.streams.hasBlockedStreams()) {
                    // has been idle long enough, but there are streams that are blocked due to
                    // flow control limits and that could have lead to the idleness.
                    // trigger sending a STREAM_DATA_BLOCKED frame for the streams
                    // to try and have their limits increased by the peer.
                    connection.streams.enqueueStreamDataBlocked();
                    if (debug.on()) {
                        debug.log("enqueued a STREAM_DATA_BLOCKED frame since connection"
                                + " has been idle due to blocked stream(s)");
                    } else {
                        Log.logQuic("{0} enqueued a STREAM_DATA_BLOCKED frame"
                                + " since connection has been idle due to"
                                + " blocked stream(s)", connection.logTag());
                    }
                }
                this.nextDeadline = timeLine().instant().plusMillis(timeoutMillis);
                return this.nextDeadline;
            } finally {
                idleTerminationLock.unlock();
            }
        }

        @Override
        public String toString() {
            return "StreamDataBlockedEvent-" + this.eventId;
        }
    }


    final class PingEvent extends TimedEvent {
        private final long pingFrequencyNanos;
        private final long idleTimeoutNanos;

        private PingEvent(final Deadline deadline, final long pingFrequencyMillis) {
            super(deadline);
            this.pingFrequencyNanos = MILLISECONDS.toNanos(pingFrequencyMillis);
            if (this.pingFrequencyNanos <= 0) {
                throw new IllegalArgumentException("ping frequency is too small: "
                        + pingFrequencyMillis + " milliseconds");
            }
            this.idleTimeoutNanos = MILLISECONDS.toNanos(getIdleTimeout().get());
        }

        @Override
        public Deadline handle() {
            if (shutdown.get()) {
                // timeout manager is shutdown, nothing more to do
                return this.nextDeadline = Deadline.MAX;
            }
            if (!shouldInitiateAppLayerCheck()) {
                // reschedule for next round
                this.nextDeadline = timeLine().instant().plusNanos(this.pingFrequencyNanos);
                return this.nextDeadline;
            }
            // check with the app layer if traffic generation is required
            final Supplier<Boolean> check = trafficGenerationCheck.get();
            if (check == null) {
                // generateTrafficCheck can be null if the timeout manager was shutdown
                // when this event handling was in progress. don't send a PING frame
                // in that case.
                assert shutdown.get() : "trafficGenerationCheck is absent";
                return this.nextDeadline = Deadline.MAX;
            }
            if (check.get()) {
                // app layer OKed sending a PING
                connection.requestSendPing();
                if (debug.on()) {
                    debug.log("enqueued a PING frame");
                } else {
                    Log.logQuic("{0} enqueued a PING frame", connection.logTag());
                }
            } else {
                // app layer told us not to send a PING.
                // we skip the PING generation only for the current round, no need
                // to disable future PING checks
                if (debug.on()) {
                    debug.log("skipping PING generation");
                } else {
                    Log.logQuic("{0} skipping PING generation", connection.logTag());
                }
            }
            this.nextDeadline = timeLine().instant().plusNanos(this.pingFrequencyNanos);
            return this.nextDeadline;
        }

        @Override
        public String toString() {
            return "PingEvent-" + this.eventId;
        }

        // returns true if the app layer traffic generation check needs to be invoked,
        // false otherwise.
        private boolean shouldInitiateAppLayerCheck() {
            final long lastPktAt = lastPacketActivityNanos;
            final long now = System.nanoTime();
            if ((now - lastPktAt) >= this.pingFrequencyNanos) {
                // no traffic during the ping interval, initiate a app layer check
                // to see if explicit traffic needs to be generated
                return true;
            }
            // check if the connection will potentially idle terminate before the next
            // ping check is scheduled, if yes, then initiate a app layer traffic
            // generation check now
            final long idleTerminationAt = lastPktAt + this.idleTimeoutNanos;
            final long nextPingCheck = now + this.pingFrequencyNanos;
            if (idleTerminationAt - nextPingCheck <= 0) {
                return true;
            }
            // connection appears to be receiving traffic, no need to initiate app layer
            // traffic generation check
            return false;
        }
    }
}
