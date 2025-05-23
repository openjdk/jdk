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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.TimeLine;
import jdk.internal.net.http.quic.ConnectionTerminator.IdleTerminationApprover;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.streams.QuicConnectionStreams;
import jdk.internal.net.quic.QuicTLSEngine;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static jdk.internal.net.http.quic.TerminationCause.forSilentTermination;

/**
 * Keeps track of activity on a {@code QuicConnectionImpl} and manages
 * the idle timeout of the QUIC connection
 */
public final class IdleTimeoutManager {

    private static final long NO_IDLE_TIMEOUT = 0;

    private final QuicConnectionImpl connection;
    private final Logger debug;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    // TODO: this shouldn't be allowed to be too low and instead should be adjusted
    // relative to PTO. see RFC-9000, section 10.1, implying ever changing value (potentially)
    private final AtomicLong idleTimeoutDurationMs = new AtomicLong();
    private final ReentrantLock timeoutEventLock = new ReentrantLock();
    // must be accessed only when holding timeoutEventLock
    private PreIdleTimeoutEvent preIdleTimeoutEvent;
    private volatile long lastActivityAt;
    private volatile IdleTerminationApprover terminationApprover;

    IdleTimeoutManager(final QuicConnectionImpl connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.debug = connection.debug;
    }

    /**
     * Starts the idle timeout management for the connection. This should be called
     * after the handshake is complete for the connection.
     *
     * @throw IllegalStateException if handshake hasn't yet completed or if the handshake
     *                              has failed for the connection
     */
    void start() {
        final CompletableFuture<QuicTLSEngine.HandshakeState> handshakeCF =
                this.connection.handshakeFlow().handshakeCF();
        // start idle management only for successfully completed handshake
        if (!handshakeCF.isDone()) {
            throw new IllegalStateException("handshake isn't yet complete,"
                    + " cannot start idle connection management");
        }
        if (handshakeCF.isCompletedExceptionally()) {
            throw new IllegalStateException("cannot start idle connection management for a failed"
                    + " connection");
        }
        startPreIdleTimer();
    }

    /**
     * Starts the pre idle timeout timer of the QUIC connection, if not already started.
     */
    private void startPreIdleTimer() {
        if (shutdown.get()) {
            return;
        }
        final TimeoutDurations timeoutDurations = this.getTimeoutDurations();
        if (timeoutDurations.preIdleTimeoutMs == NO_IDLE_TIMEOUT) {
            return;
        }
        final QuicTimerQueue timerQueue = connection.endpoint().timer();
        final Deadline deadline = timeLine().instant().plusMillis(timeoutDurations.preIdleTimeoutMs);
        this.timeoutEventLock.lock();
        try {
            // we don't expect idle timeout management to be started more than once
            assert this.preIdleTimeoutEvent == null : "idle timeout management"
                    + " already started for connection";
            // create the pre idle timeout event and register with the QuicTimerQueue.
            this.preIdleTimeoutEvent = new PreIdleTimeoutEvent(deadline);
            timerQueue.offer(this.preIdleTimeoutEvent);
            if (debug.on()) {
                debug.log("started QUIC idle timeout management for connection,"
                        + " pre idle timeout event: " + this.preIdleTimeoutEvent
                        + " deadline: " + deadline);
            }  else {
                Log.logQuic("{0} started QUIC idle timeout management for connection,"
                                + " pre idle timeout event: {1} deadline: {2}",
                        connection.logTag(), this.preIdleTimeoutEvent, deadline);
            }
        } finally {
            this.timeoutEventLock.unlock();
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

    void registerTerminationApprover(final IdleTerminationApprover approver) {
        this.terminationApprover = approver;
    }

    void keepAlive() {
        lastActivityAt = System.nanoTime(); // TODO: timeline().instant()?
    }

    private void keepAliveWithPing() {
        keepAlive();
        if (debug.on()) {
            debug.log("sending PING to keep the connection alive");
        } else {
            Log.logQuic("{0} sending PING to keep the connection alive", connection.logTag());
        }
        var _ = this.connection.requestSendPing();
    }

    void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            // already shutdown
            return;
        }
        // unregister the timeout event(s) from the QuicTimerQueue
        // so that the timer queue doesn't hold on to the (Pre)IdleTimeoutEvent (and thus
        // the QuicConnectionImpl instance) until the event fires for the next time.
        this.timeoutEventLock.lock();
        try {
            if (this.preIdleTimeoutEvent != null) {
                final QuicEndpoint endpoint = this.connection.endpoint();
                assert endpoint != null : "QUIC endpoint is null";
                // disable the event (refreshDeadline() of IdleTimeoutEvent will return Deadline.MAX)
                Deadline nextDeadline = this.preIdleTimeoutEvent.nextDeadline;
                if (!nextDeadline.equals(Deadline.MAX)) {
                    this.preIdleTimeoutEvent.nextDeadline = Deadline.MAX;
                    endpoint.timer().reschedule(this.preIdleTimeoutEvent, Deadline.MIN);
                }
            }
        } finally {
            this.timeoutEventLock.unlock();
        }
        if (debug.on()) {
            debug.log("idle timeout manager shutdown");
        }
    }

    public void localIdleTimeout(final long timeoutMillis) {
        checkUpdateIdleTimeout(timeoutMillis);
    }

    public void peerIdleTimeout(final long timeoutMillis) {
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

    private TimeoutDurations getTimeoutDurations() {
        final long idleTimeoutMs = getIdleTimeout().orElse(NO_IDLE_TIMEOUT);
        if (idleTimeoutMs == NO_IDLE_TIMEOUT) {
            return new TimeoutDurations(NO_IDLE_TIMEOUT, 0);
        }
        // pre-idle timeout duration = (arbitrary) 80% of idle timeout duration
        final long preIdleTimeoutMs = (long) Math.ceil(0.8 * idleTimeoutMs);
        final long delta = idleTimeoutMs - preIdleTimeoutMs;
        return new TimeoutDurations(preIdleTimeoutMs, delta);
    }

    private record TimeoutDurations(long preIdleTimeoutMs, long deltaToIdleTimeoutMs) {
    }

    final class PreIdleTimeoutEvent implements QuicTimedEvent {
        private final long eventId;
        private volatile Deadline deadline;
        private volatile Deadline nextDeadline;

        private PreIdleTimeoutEvent(final Deadline deadline) {
            assert deadline != null : "timeout deadline is null";
            this.deadline = this.nextDeadline = deadline;
            this.eventId = QuicTimerQueue.newEventId();
        }

        @Override
        public Deadline deadline() {
            return this.deadline;
        }

        @Override
        public Deadline refreshDeadline() {
            if (shutdown.get()) {
                return this.deadline = this.nextDeadline = Deadline.MAX;
            }
            return this.deadline = this.nextDeadline;
        }

        @Override
        public Deadline handle() {
            if (shutdown.get()) {
                // timeout manager is shutdown, nothing more to do
                return this.nextDeadline = Deadline.MAX;
            }
            final Deadline taskStartedAt = timeLine().instant();
            final TimeoutDurations timeoutDurations = getTimeoutDurations();
            if (timeoutDurations.preIdleTimeoutMs == NO_IDLE_TIMEOUT) {
                return Deadline.MAX;
            }
            // check whether the connection has indeed been idle for the preIdleTimeout duration
            Deadline postponed = maybePostponeDeadline(timeoutDurations.preIdleTimeoutMs);
            if (postponed != null) {
                // not idle long enough, reschedule
                this.nextDeadline = postponed;
                return postponed;
            }
            // let the higher (application) layer know that we have reached pre idle timeout
            // and would like to terminate the connection some reasonably long duration from
            // now. If they want to keep the connection alive, they will disapprove the
            // idle timeout termination, in which case we mark the connection alive and
            // reschedule the pre idle timeout timer.
            final IdleTerminationApprover approver = terminationApprover;
            boolean allowedToIdleTimeout = true;
            if (approver != null) {
                try {
                    allowedToIdleTimeout = approver.isAllowedToIdleTimeout();
                } catch (Throwable _) {
                }
            }
            if (!allowedToIdleTimeout) {
                // the higher layer disallowed the idle termination for this round.
                // we mark the connection as active at this moment.
                if (debug.on()) {
                    debug.log("idle termination disapproved");
                }
                // we had reached the pre idle timeout (due to lack of traffic on the connection),
                // yet the higher layer wants us to keep the connection alive. we thus explicitly
                // send a ping to generate traffic on the connection and prevent the peer from
                // idle timing out the connection
                keepAliveWithPing();
                // postpone and reschedule the pre idle timeout timer
                final Deadline p = maybePostponeDeadline(timeoutDurations.preIdleTimeoutMs);
                // we expect it to be postponed
                assert p != null : "postponed deadline is null";
                this.nextDeadline = p;
                return p;
            }
            // the connection has been idle for the pre idle timeout duration
            // and has been approved to be terminated by the higher layer.
            // we now schedule a idle timeout event that will terminate the connection
            // at the negotiated time.
            final Deadline idleTimeoutDeadline = taskStartedAt.plusMillis(
                    timeoutDurations.deltaToIdleTimeoutMs);
            scheduleIdleTimeout(idleTimeoutDeadline);
            return this.nextDeadline = Deadline.MAX;
        }

        private Deadline maybePostponeDeadline(final long expectedIdleDurationMs) {
            final long lastActiveNanos = lastActivityAt;
            final long currentNanos = System.nanoTime();
            final long inactivityMs = MILLISECONDS.convert((currentNanos - lastActiveNanos),
                    NANOSECONDS);
            if (inactivityMs >= expectedIdleDurationMs) {
                final QuicConnectionStreams connStreams = connection.streams;
                if (!connStreams.hasBlockedStreams()) {
                    // has been idle long enough and there aren't any streams that could have
                    // generated traffic on the connection but couldn't due to being blocked by
                    // flow control limits.
                    return null;
                }
                // has been idle long enough, but there are streams that are blocked due to
                // flow control limits and that could have lead to the idleness.
                // trigger sending a STREAM_DATA_BLOCKED frame for the streams
                // to try and have their limits increased by the peer. also, postpone
                // the idle timeout deadline to give the connection a chance to be active
                // again.
                connStreams.enqueueStreamDataBlocked();
                final Deadline next = timeLine().instant().plusMillis(expectedIdleDurationMs);
                if (debug.on()) {
                    debug.log("streams blocked due to flow control limits, postponing "
                            + " timeout event: " + this + " to fire in " + expectedIdleDurationMs
                            + " milli seconds, deadline: " + next);
                }
                return next;
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

        private void scheduleIdleTimeout(final Deadline idleTimeoutDeadline) {
            if (shutdown.get()) {
                return;
            }
            final QuicTimerQueue timerQueue = connection.endpoint().timer();
            // create the idle timeout event and register with the QuicTimerQueue.
            final IdleTimeoutEvent idleTimeoutEvent = new IdleTimeoutEvent(idleTimeoutDeadline);
            timerQueue.offer(idleTimeoutEvent);
            if (debug.on()) {
                debug.log("registered idle timeout event: " + idleTimeoutEvent
                        + " deadline: " + idleTimeoutDeadline);
            } else {
                Log.logQuic("{0} registered idle timeout event: {1} deadline: {2}",
                        connection.logTag(), idleTimeoutEvent, idleTimeoutDeadline);
            }
        }

        @Override
        public long eventId() {
            return this.eventId;
        }

        @Override
        public String toString() {
            return "QuicPreIdleTimeoutEvent-" + this.eventId;
        }
    }

    final class IdleTimeoutEvent implements QuicTimedEvent {
        private final long eventId;
        private volatile Deadline deadline;
        private volatile Deadline nextDeadline;

        private IdleTimeoutEvent(final Deadline deadline) {
            this.eventId = QuicTimerQueue.newEventId();
            this.deadline = this.nextDeadline = deadline;
        }

        @Override
        public Deadline deadline() {
            return this.deadline;
        }

        @Override
        public Deadline handle() {
            try {
                idleTimedOut();
            } finally {
                // the connection was idle timed out, we no longer
                // manage the connection
                shutdown();
            }
            // don't reschedule, since the connection has been timed out
            return this.nextDeadline = Deadline.MAX;
        }

        @Override
        public long eventId() {
            return this.eventId;
        }

        @Override
        public Deadline refreshDeadline() {
            if (shutdown.get()) {
                return this.deadline = this.nextDeadline = Deadline.MAX;
            }
            return this.deadline = this.nextDeadline;
        }

        @Override
        public String toString() {
            return "QuicIdleTimeoutEvent-" + this.eventId;
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

        // log idle timeout, with packet space statistics
        String msg = "silently closing connection due to idle timeout (" + timeoutMillis
                + " milli seconds)";
        StringBuilder sb = new StringBuilder();
        for (PacketNumberSpace sp : PacketNumberSpace.values()) {
            if (sp == PacketNumberSpace.NONE) continue;
            if (connection.packetNumberSpaces().get(sp) instanceof PacketSpaceManager m) {
                sb.append("\n  PacketSpace: ").append(sp).append('\n');
                m.debugState("    ", sb);
            }
        }
        if (Log.quic()) {
            if (debug.on()) debug.log(msg);
            Log.logQuic("{0} {1}: {2}", connection.logTag(), msg, sb.toString());
        } else if (debug.on()) {
            debug.log("%s: %s", msg, sb);
        }

        // silently close the connection and discard all its state
        final TerminationCause cause = forSilentTermination("connection idle timed out ("
                + timeoutMillis + " milli seconds)");
        connection.terminator.terminate(cause);
    }
}
