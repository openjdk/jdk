/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.TimeLine;
import jdk.internal.net.http.common.TimeSource;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.frames.AckFrame;
import jdk.internal.net.http.quic.frames.AckFrame.AckFrameBuilder;
import jdk.internal.net.http.quic.frames.ConnectionCloseFrame;
import jdk.internal.net.http.quic.packets.PacketSpace;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketType;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicOneRttContext;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;

/**
 * A {@code PacketSpaceManager} takes care of acknowledgement and
 * retransmission of packets for a given {@link PacketNumberSpace}.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9002
 *      RFC 9002: QUIC Loss Detection and Congestion Control
 */

// See also: RFC 9000, https://www.rfc-editor.org/rfc/rfc9000#name-sending-ack-frames
//   Every packet SHOULD be acknowledged at least once, and
//   ack-eliciting packets MUST be acknowledged at least once within
//   the maximum delay an endpoint communicated using the max_ack_delay
//   transport parameter [...];
//   [...]
//   In order to assist loss detection at the sender, an endpoint
//   SHOULD generate and send an ACK frame without delay when it
//   receives an ack-eliciting packet either:
//      - when the received packet has a packet number less
//        than another ack-eliciting packet that has been received, or
//      - when the packet has a packet number larger than the
//        highest-numbered ack-eliciting packet that has been received
//        and there are missing packets between that packet and this
//        packet. [...]
public sealed class PacketSpaceManager implements PacketSpace
        permits PacketSpaceManager.OneRttPacketSpaceManager,
                PacketSpaceManager.HandshakePacketSpaceManager {

    private final QuicCongestionController congestionController;
    private volatile boolean blockedByCC;
    // packet threshold for loss detection; RFC 9002 suggests 3
    private static final long kPacketThreshold = 3;
    // Multiplier for persistent congestion; RFC 9002 suggests 3
    private static final int kPersistentCongestionThreshold = 3;

    /**
     * A record that stores the next AckFrame that should be sent
     * within this packet number space.
     *
     * @param ackFrame the ACK frame to send.
     * @param deadline the deadline by which to send this ACK frame.
     * @param lastUpdated the time at which the {@link AckFrame}'s
     *                   {@link AckFrame#largestAcknowledged()} was
     *                    last updated. Used for calculating ack delay.
     * @param sent the time at which the {@link AckFrame} was sent,
     *             or {@code null} if it has not been sent yet.
     */
    record NextAckFrame(AckFrame ackFrame,
                        Deadline deadline,
                        Deadline lastUpdated,
                        Deadline sent) {
        /**
         * {@return an identical {@code NextAckFrame} record, with an updated
         *  {@code deadline}}
         * @param deadline the new deadline
         * @param sent     the point in time at which the ack frame was sent, or null.
         */
        public NextAckFrame withDeadline(Deadline deadline, Deadline sent) {
            return new NextAckFrame(ackFrame, deadline, lastUpdated, sent);
        }
    }

    // true if transmit timer should fire now
    private volatile boolean transmitNow;

    // These two numbers control whether an PING frame will be
    // sent with the next ACK frame, to turn the packet that
    // contains the ACK frame into an ACK-eliciting packet.
    // These numbers are *not* defined in RFC 9000, but are used
    // to implement a strategy for sending occasional PING frames
    // in order to prevent ACK frames from growing too big.
    // See RFC 9000 section 13.2.4
    // https://www.rfc-editor.org/rfc/rfc9000#name-limiting-ranges-by-tracking
    public static final int MAX_ACKRANGE_COUNT_BEFORE_PING = 10;

    protected final Logger debug;
    private final Supplier<String> debugStrSupplier;
    private final PacketNumberSpace packetNumberSpace;
    private final PacketEmitter packetEmitter;
    private final ConnectionTerminator terminator;
    private final ReentrantLock transferLock = new ReentrantLock();
    // The next packet number to use in this space
    private final AtomicLong nextPN = new AtomicLong();
    private final TimeLine instantSource;
    private final QuicRttEstimator rttEstimator;
    // first packet number sent after handshake confirmed
    private long handshakeConfirmedPN;

    // A priority queue containing a record for each unacknowledged PingRequest.
    // PingRequest are removed from this queue when they are acknowledged, that
    // is when any packet whose number is greater than the request packet
    // is acknowledged.
    // Note: this is used to implement {@link #requestSendPing()} which is
    //       used to implemeent out of bound ping requests triggered by the
    //       application.
    private final ConcurrentLinkedQueue<PingRequest> pendingPingRequests =
            new ConcurrentLinkedQueue<>();

    // A priority queue containing a record for each unacknowledged packet.
    // Packets are removed from this queue when they are acknowledged, or when they
    // are being retransmitted. In which case, they will be in the pendingRetransmission
    // queue
    private final ConcurrentLinkedQueue<PendingAcknowledgement> pendingAcknowledgements =
            new ConcurrentLinkedQueue<>();

    // A map containing send times of ack-eliciting packets.
    // Packets are removed from this map when they can't contribute to RTT sample,
    // i.e. when they are acknowledged, or when a higher-numbered packet is acknowledged.
    private final ConcurrentSkipListMap<Long, Deadline> sendTimes =
            new ConcurrentSkipListMap<>();

    // A priority queue containing a record for each unacknowledged packet whose deadline
    // is due, and which is currently being retransmitted.
    // Packets are removed from this queue when they have been scheduled for retransmission
    // with the quic endpoint
    private final ConcurrentLinkedQueue<PendingAcknowledgement> pendingRetransmission =
            new ConcurrentLinkedQueue<>();

    // A priority queue containing a record for each unacknowledged packet whose deadline
    // is due, and which should be retransmitted.
    // Packets are removed from this queue when they have been scheduled for encryption.
    private final ConcurrentLinkedQueue<PendingAcknowledgement> triggeredForRetransmission =
            new ConcurrentLinkedQueue<>();

    // lost packets
    private final ConcurrentLinkedQueue<PendingAcknowledgement> lostPackets =
            new ConcurrentLinkedQueue<>();

    // A task invoked by the QuicTimerQueue when some packet retransmission are
    // due. This task will move packets from the pendingAcknowledgement queue
    // into the triggeredForRetransmission queue (and pendingRetransmission queue)
    private final PacketTransmissionTask packetTransmissionTask;
    private volatile boolean fastRetransmitDone;
    private volatile boolean fastRetransmit;

    /**
     * A record to store previous numbers with which a packet has been
     * retransmitted. If such a packet is acknowledged, we can stop
     * retransmission.
     *
     * @param number   A packet number with which the content of this
     *                 packet was previously sent.
     * @param largestAcknowledged the largest packet number acknowledged by this
     *                            previous packet, or {@code -1L} if no packet was
     *                            acknowledged by this packet.
     * @param previous Further previous packet numbers, or {@code null}.
     */
    private record PreviousNumbers(long number,
                                   long largestAcknowledged, PreviousNumbers previous) {}

    /**
     * A record used to implement {@link #requestSendPing()}.
     * @param sent when the ping frame was sent
     * @param packetNumber the packet number of the packet containing the pingframe
     * @param response the response, which will be complete as soon as a packet whose number is
     *                 >= to {@code packetNumber} is received.
     */
    private record PingRequest(Deadline sent, long packetNumber, CompletableFuture<Long> response) {}

    /**
     * A record to store a packet that hasn't been acknowledged, and should
     * be scheduled for retransmission if not acknowledged when the deadline
     * is reached.
     *
     * @param packet the unacknowledged quic packet
     * @param sent the instant when the packet was sent.
     * @param packetNumber the packet number of the {@code packet}
     * @param largestAcknowledged the largest packet number acknowledged by this
     *                            packet, or {@code -1L} if no packet is acknowledged
     *                            by this packet.
     * @param previousNumbers previous packet numbers with which the packet was
     *                        transmitted, if any, {@code null} otherwise.
     */
    private record PendingAcknowledgement(QuicPacket packet, Deadline sent,
                                           long packetNumber, long largestAcknowledged,
                                           PreviousNumbers previousNumbers) {

        PendingAcknowledgement(QuicPacket packet, Deadline sent,
                               long packetNumber, PreviousNumbers previousNumbers) {
            this(packet, sent, packetNumber,
                    AckFrame.largestAcknowledgedInPacket(packet), previousNumbers);
        }

        boolean hasPreviousNumber(long packetNumber) {
            if (this.packetNumber <= packetNumber) return false;
            var pn = previousNumbers;
            while (pn != null) {
                if (pn.number == packetNumber) {
                    return true;
                }
                pn = pn.previous;
            }
            return false;
        }
        boolean hasExactNumber(long packetNumber) {
            return this.packetNumber == packetNumber;
        }
        boolean hasNumber(long packetNumber) {
            return  this.packetNumber == packetNumber || hasPreviousNumber(packetNumber);
        }
        PreviousNumbers findPreviousAcknowledged(AckFrame frame) {
            var pn = previousNumbers;
            while (pn != null) {
                if (frame.isAcknowledging(pn.number)) return pn;
                pn = pn.previous;
            }
            return null;
        }
        boolean isAcknowledgedBy(AckFrame frame) {
            if (frame.isAcknowledging(packetNumber)) return true;
            else return findPreviousAcknowledged(frame) != null;
        }

        public int attempts() {
            var pn = previousNumbers;
            int count = 0;
            while (pn != null) {
                count++;
                pn = pn.previous;
            }
            return count;
        }

        String prettyPrint() {
            StringBuilder b = new StringBuilder();
            b.append("pn:").append(packetNumber);
            var ppn = previousNumbers;
            if (ppn != null) {
                var sep = " [";
                while (ppn != null) {
                    b.append(sep).append(ppn.number);
                    ppn = ppn.previous;
                    sep = ", ";
                }
                b.append("]");
            }
            return b.toString();
        }
    }

    /**
     * A task that sends packets to the peer.
     *
     * Packets are sent after a delay when:
     * - ack delay timer expires
     * - PTO timer expires
     * They can also be sent without delay when:
     * - we are unblocked by the peer
     * - new data is available for sending, and we are not blocked
     * - need to send ack without delay
     */
    public final class PacketTransmissionTask implements QuicTimedEvent {
        private final SequentialScheduler handleScheduler =
                SequentialScheduler.lockingScheduler(this::handleLoop);
        private final long id = QuicTimerQueue.newEventId();
        private volatile Deadline nextDeadline; // updated through VarHandle
        private PacketTransmissionTask() {
            nextDeadline = Deadline.MAX;
        }

        @Override
        public long eventId() { return id; }

        @Override
        public Deadline deadline() {
            return nextDeadline;
        }

        @Override
        public Deadline handle() {
            if (closed) {
                if (debug.on()) {
                    debug.log("packet space already closed, PacketTransmissionTask will" +
                            " no longer be scheduled");
                }
                return Deadline.MAX;
            }
            handleScheduler.runOrSchedule(packetEmitter.executor());
            return Deadline.MAX;
        }

        /**
         * The handle loop takes care of sending ACKs, packaging stream data
         * (if applicable), and retransmitting on PTO. It is never invoked
         * directly - but can be triggered by {@link #handle()} or {@link
         * #runTransmitter()}
         */
        private void handleLoop() {
            try {
                handleLoop0();
            } catch (Throwable t) {
                if (Log.errors()) {
                    Log.logError("{0}: {1} handleLoop failed: {2}",
                            packetEmitter.logTag(), packetNumberSpace, t);
                    Log.logError(t);
                } else if (debug.on()) {
                    debug.log("handleLoop failed", t);
                }
            }
        }

        private void handleLoop0() throws IOException, QuicTransportException {
            // while congestion control allows, or if PTO expired:
            // - send lost packet or new packet
            // if PTO still expired (== nothing was sent)
            // - resend oldest packet, if available
            // - otherwise send ping (+ack, if available)
            // if ACK still not sent, send ack
            if (debug.on()) {
                debug.log("PacketTransmissionTask::handle");
            }
            packetEmitter.checkAbort(PacketSpaceManager.this.packetNumberSpace);
            // Handle is called from within the executor
            var nextDeadline = this.nextDeadline;
            Deadline now = now();
            do {
                transmitNow = false;
                var closed = !isOpenForTransmission();
                if (closed) {
                    if (debug.on()) {
                        debug.log("PacketTransmissionTask::handle: %s closed",
                                PacketSpaceManager.this.packetNumberSpace);
                    }
                    return;
                }
                if (debug.on()) debug.log("PacketTransmissionTask::handle");
                // this may update congestion controller
                int lost = detectAndRemoveLostPackets(now);
                if (lost > 0 && debug.on()) debug.log("handle: found %s lost packets", lost);
                // if we're sending on PTO, we need to double backoff afterwards
                boolean needBackoff = isPTO(now);
                int packetsSent = 0;
                boolean cwndAvailable;
                while ((cwndAvailable = congestionController.canSendPacket()) ||
                        (needBackoff && packetsSent < 2)) { // if PTO, try to send 2 packets
                    if (!isOpenForTransmission()) {
                        break;
                    }
                    final boolean retransmitted;
                    try {
                        retransmitted = retransmit();
                    } catch (QuicKeyUnavailableException qkue) {
                        if (!isOpenForTransmission()) {
                            if (debug.on()) {
                                debug.log("already closed; not re-transmitting any more data");
                            }
                            clearAll();
                            return;
                        }
                        throw new IOException("failed to retransmit data, reason: "
                                + qkue.getMessage());
                    }
                    if (retransmitted) {
                        packetsSent++;
                        continue;
                    }
                    final boolean sentNew;
                    // nothing was retransmitted - check for new data
                    try {
                        sentNew = sendNewData();
                    } catch (QuicKeyUnavailableException qkue) {
                        if (!isOpenForTransmission()) {
                            if (debug.on()) {
                                debug.log("already closed; not transmitting any more data");
                            }
                            return;
                        }
                        throw new IOException("failed to send new data, reason: "
                                + qkue.getMessage());
                    }
                    if (!sentNew) {
                        break;
                    }
                    packetsSent++;
                }
                blockedByCC = !cwndAvailable;
                if (!cwndAvailable && isOpenForTransmission()) {
                    if (debug.on()) debug.log("handle: blocked by CC");
                    // CC might be available already
                    if (congestionController.canSendPacket()) {
                        if (debug.on()) debug.log("handle: unblocked immediately");
                        transmitNow = true;
                    }
                }
                try {
                    if (isPTO(now) && isOpenForTransmission()) {
                        if (debug.on()) debug.log("handle: retransmit on PTO");
                        // nothing was sent by the above loop - try to resend the oldest packet
                        retransmitPTO();
                    } else if (fastRetransmit) {
                        assert packetNumberSpace == PacketNumberSpace.INITIAL;
                        fastRetransmitDone = true;
                        fastRetransmit = false;
                        if (debug.on()) debug.log("handle: fast retransmit");
                        // try to resend the oldest packet
                        retransmitPTO();
                    }
                } catch (QuicKeyUnavailableException qkue) {
                    if (!isOpenForTransmission()) {
                        if (debug.on()) {
                            debug.log("already closed; not re-transmitting any more data");
                        }
                        return;
                    }
                    throw new IOException("failed to retransmitPTO data, key space, reason: "
                            + qkue.getMessage());
                }
                boolean stillPTO = isPTO(now);
                // if the ack frame is not sent yet, send it now
                var ackFrame = getNextAckFrame(!stillPTO);
                boolean sendPing = pingRequested != null || stillPTO
                        || shouldSendPing(now, ackFrame);
                if (sendPing || ackFrame != null) {
                    if (debug.on()) debug.log("handle: generate ACK packet or PING ack:%s ping:%s",
                            ackFrame != null, sendPing);
                    final long emitted;
                    try {
                        emitted = emitAckPacket(ackFrame, sendPing);
                    } catch (QuicKeyUnavailableException qkue) {
                        if (!isOpenForTransmission()) {
                            if (debug.on()) {
                                debug.log("already closed; not sending ack/ping packet");
                            }
                            return;
                        }
                        throw new IOException("failed to send ack/ping data, reason: "
                                + qkue.getMessage());
                    }
                    if (sendPing && pingRequested != null) {
                        if (emitted < 0) pingRequested.complete(-1L);
                        else registerPingRequest(new PingRequest(now, emitted, pingRequested));
                        pingRequested = null;
                    }
                }
                if (needBackoff) {
                    long backoff = rttEstimator.increasePtoBackoff();
                    if (debug.on()) {
                        debug.log("handle: %s increase backoff to %s",
                                PacketSpaceManager.this.packetNumberSpace,
                                backoff);
                    }
                    packetEmitter.ptoBackoffIncreased(PacketSpaceManager.this, backoff);
                }

                // if nextDeadline is not Deadline.MAX the task will be
                // automatically rescheduled.
                if (debug.on()) debug.log("handle: refreshing deadline");
                nextDeadline = computeNextDeadline();
            } while(!nextDeadline.isAfter(now));

            logNoDeadline(nextDeadline, true);
            if (Deadline.MAX.equals(nextDeadline)) return;
            // we have a new deadline
            packetEmitter.reschedule(this, nextDeadline);
        }

        /**
         * Create and send a new packet
         * @return true if packet was sent, false if there is no more data to send
         */
        private boolean sendNewData() throws QuicKeyUnavailableException, QuicTransportException {
            if (debug.on()) debug.log("handle: sending data...");
            boolean sent = packetEmitter.sendData(packetNumberSpace);
            if (!sent) {
                if (debug.on()) debug.log("handle: no more data to send");
            }
            return sent;
        }

        @Override
        public Deadline refreshDeadline() {
            Deadline previousDeadline, newDeadline;
            do {
                previousDeadline = this.nextDeadline;
                newDeadline = computeNextDeadline();
            } while (!Handles.DEADLINE.compareAndSet(this, previousDeadline, newDeadline));

            if (!newDeadline.equals(previousDeadline)) {
                if (debug.on()) {
                    var now = now();
                    if (newDeadline.equals(Deadline.MAX)) {
                        debug.log("Deadline refreshed: no new deadline");
                    } else if (newDeadline.equals(Deadline.MIN)) {
                        debug.log("Deadline refreshed: run immediately");
                    } else if (previousDeadline.equals(Deadline.MAX) || previousDeadline.equals(Deadline.MIN)) {
                        var delay = now.until(newDeadline, ChronoUnit.MILLIS);
                        if (delay < 0) {
                            debug.log("Deadline refreshed: new deadline passed by %dms", delay);
                        } else {
                            debug.log("Deadline refreshed: new deadline in %dms", delay);
                        }
                    } else {
                        var delay = now.until(newDeadline, ChronoUnit.MILLIS);
                        if (delay < 0) {
                            debug.log("Deadline refreshed: new deadline passed by %dms (diff: %dms)",
                                    delay, previousDeadline.until(newDeadline, ChronoUnit.MILLIS));
                        } else {
                            debug.log("Deadline refreshed: new deadline in %dms (diff: %dms)",
                                    instantSource.instant().until(newDeadline, ChronoUnit.MILLIS),
                                    previousDeadline.until(newDeadline, ChronoUnit.MILLIS));
                        }
                    }
                }
            } else {
                debug.log("Deadline not refreshed: no change");
            }
            logNoDeadline(newDeadline, false);
            return newDeadline;
        }

        void logNoDeadline(Deadline newDeadline, boolean onlyNoDeadline) {
            if (Log.quicRetransmit()) {
                if (Deadline.MAX.equals(newDeadline)) {
                    if (shouldLogWhenNoDeadline()) {
                        Log.logQuic("{0}: {1} no deadline, task unscheduled",
                                packetEmitter.logTag(), packetNumberSpace);
                    } // else: no changes...
                } else if (!onlyNoDeadline && shouldLogWhenNewDeadline()) {
                    if (Deadline.MIN.equals(newDeadline)) {
                        Log.logQuic("{0}: {1} Deadline.MIN, task will be rescheduled immediately",
                                packetEmitter.logTag(), packetNumberSpace);
                    } else {
                        try {
                            Log.logQuic("{0}: {1} new deadline computed, deadline in {2}ms",
                                    packetEmitter.logTag(), packetNumberSpace,
                                    Long.toString(now().until(newDeadline, ChronoUnit.MILLIS)));
                        } catch (ArithmeticException ae) {
                            Log.logError("Unexpected exception while logging deadline "
                                    + newDeadline + ": " + ae);
                            Log.logError(ae);
                            assert false : "Unexpected ArithmeticException: " + ae;
                        }
                    }
                }
            }
        }

        private boolean hadNoDeadline;
        private synchronized boolean shouldLogWhenNoDeadline() {
            if (!hadNoDeadline) {
                hadNoDeadline = true;
                return true;
            }
            return false;
        }

        private synchronized boolean shouldLogWhenNewDeadline() {
            if (hadNoDeadline) {
                hadNoDeadline = false;
                return true;
            }
            return false;
        }

        boolean hasNoDeadline() {
            return Deadline.MAX.equals(nextDeadline);
        }

        // reschedule this task
        void reschedule() {
            Deadline deadline = computeNextDeadline();
            Deadline nextDeadline = this.nextDeadline;
            if (Deadline.MAX.equals(deadline)) {
                debug.log("no deadline, don't reschedule");
            } else if (deadline.equals(nextDeadline)) {
                debug.log("deadline unchanged, don't reschedule");
            } else {
                packetEmitter.reschedule(this, deadline);
                debug.log("retransmission task: rescheduled");
            }
        }

        @Override
        public String toString() {
            return "PacketTransmissionTask(" + debugStrSupplier.get() + ")";
        }
    }

    Deadline deadline() {
        return packetTransmissionTask.deadline();
    }

    Deadline prospectiveDeadline() {
        return computeNextDeadline(false);
    }

    // remove all pending acknowledgements and retransmissions.
    private void clearAll() {
        transferLock.lock();
        try {
            pendingAcknowledgements.forEach(ack -> congestionController.packetDiscarded(List.of(ack.packet)));
            if (debug.on()) {
                final StringBuilder sb = new StringBuilder();
                pendingAcknowledgements.forEach((p) -> sb.append(" ").append(p));
                if (!sb.isEmpty()) {
                    debug.log("forgetting pending acks: " + sb);
                }
            }
            pendingAcknowledgements.clear();

            if (debug.on()) {
                final StringBuilder sb = new StringBuilder();
                pendingRetransmission.forEach((p) -> sb.append(" ").append(p));
                if (!sb.isEmpty()) {
                    debug.log("forgetting pending retransmissions: " + sb);
                }
            }
            pendingRetransmission.clear();

            if (debug.on()) {
                final StringBuilder sb = new StringBuilder();
                triggeredForRetransmission.forEach((p) -> sb.append(" ").append(p));
                if (!sb.isEmpty()) {
                    debug.log("forgetting triggered-for-retransmissions: " + sb.toString());
                }
            }
            triggeredForRetransmission.clear();

            if (debug.on()) {
                final StringBuilder sb = new StringBuilder();
                lostPackets.forEach((p) -> sb.append(" ").append(p));
                if (!sb.isEmpty()) {
                    debug.log("forgetting lost-packets: " + sb.toString());
                }
            }
            lostPackets.clear();
        } finally {
            transferLock.unlock();
        }
    }

    private void retransmitPTO() throws QuicKeyUnavailableException, QuicTransportException {
        if (!isOpenForTransmission()) {
            if (debug.on()) {
                debug.log("already closed; retransmission on PTO dropped", packetNumberSpace);
            }
            clearAll();
            return;
        }

        PendingAcknowledgement pending;
        transferLock.lock();
        try {
            if ((pending = pendingAcknowledgements.poll()) != null) {
                if (debug.on()) debug.log("Retransmit on PTO: looking for candidate");
                // TODO should keep this packet on the list until it's either acked or lost
                congestionController.packetDiscarded(List.of(pending.packet));
                pendingRetransmission.add(pending);
            }
        } finally {
            transferLock.unlock();
        }
        if (pending != null) {
            packetEmitter.retransmit(this, pending.packet(), pending.attempts());
        }
    }

    /**
     * {@return true if this packet space isn't closed and if the underlying packet emitter
     *  is open, else returns false}
     */
    private boolean isOpenForTransmission() {
        return !this.closed && this.packetEmitter.isOpen();
    }

    /**
     * A class to keep track of the largest packet that was acknowledged by
     * a packet that is being acknowledged.
     * This information is used to implement the algorithm described in
     * RFC 9000 13.2.4. Limiting Ranges by Tracking ACK Frames
     */
    private final class EmittedAckTracker {
        volatile long ignoreAllPacketsBefore = -1;
        /**
         * Record the {@link AckFrame#largestAcknowledged()
         * largest acknowledged} packet that was sent in an
         * {@link AckFrame} that the peer has acknowledged.
         * @param largestAcknowledged the packet number to record
         * @return the largest {@code largestAcknowledged}
         *         packet number that was recorded.
         *         This is necessarily smaller than (or equal to) the
         *         {@link #getLargestSentAckedPN()}.
         */
        private long record(long largestAcknowledged) {
            long witness;
            long largestSentAckedPN = PacketSpaceManager.this.largestSentAckedPN;
            do {
                witness = largestAckedPNReceivedByPeer;
                if (witness >= largestAcknowledged) {
                    largestSentAckedPN = PacketSpaceManager.this.largestSentAckedPN;
                    assert witness <= largestSentAckedPN || ignoreAllPacketsBefore > largestSentAckedPN
                            : "largestAckedPNReceivedByPeer: %s, ignoreAllPacketsBefore: %s, largestSentAckedPN: %s"
                            .formatted(witness, ignoreAllPacketsBefore, largestSentAckedPN);
                    return witness;
                }
            } while (!Handles.LARGEST_ACK_ACKED_PN.compareAndSet(
                    PacketSpaceManager.this, witness, largestAcknowledged));
            assert witness <= largestAcknowledged;
            assert largestAcknowledged <= largestSentAckedPN || ignoreAllPacketsBefore > largestSentAckedPN
                    : "largestAcknowledged: %s, ignoreAllPacketsBefore: %s, largestSentAckedPN: %s"
                    .formatted(largestSentAckedPN, ignoreAllPacketsBefore, largestSentAckedPN);
            return largestAcknowledged;
        }

        private boolean ignoreAllPacketsBefore(long packetNumber) {
            long ignoreAllPacketsBefore;
            do {
                ignoreAllPacketsBefore = this.ignoreAllPacketsBefore;
                if (packetNumber <= ignoreAllPacketsBefore) return false;
            } while (!Handles.IGNORE_ALL_PN_BEFORE.compareAndSet(
                    this, ignoreAllPacketsBefore, packetNumber));
            return true;
        }

        /**
         * Tracks the largest packet acknowledged by the packets acknowledged in the
         * given AckFrame. This helps to implement the algorithm described in
         * RFC 9000,  13.2.4. Limiting Ranges by Tracking ACK Frames.
         * @param pending a yet unacknowledged packet that may be acknowledged
         *                by the given{@link AckFrame}.
         * @param frame a received {@code AckFrame}
         * @return whether the given pending unacknowledged packet is being
         *         acknowledged by this ack frame.
         */
        public boolean trackAcknowlegment(PendingAcknowledgement pending, AckFrame frame) {
            if (frame.isAcknowledging(pending.packetNumber)) {
                record(pending.largestAcknowledged);
                packetEmitter.acknowledged(pending.packet());
                return true;
            }
            // There is a potential for a never ending retransmission
            // loop here if we don't treat the ack of a previous packet just
            // as the ack of the tip of the chain.
            // So we call packetEmitter.acknowledged(pending.packet()) here too,
            // and return `true` in this case as well.
            var previous = pending.findPreviousAcknowledged(frame);
            if (previous != null) {
                record(previous.largestAcknowledged);
                packetEmitter.acknowledged(pending.packet());
                return true;
            }
            return false;
        }

        public long largestAckAcked() {
            return largestAckedPNReceivedByPeer;
        }

        public void dropPacketNumbersSmallerThan(long newLargestIgnored) {
            // this method is called after arbitrarily reducing the ack range
            // to this value; This mean we will drop packets whose packet
            // number is smaller than the given packet number.
            if (ignoreAllPacketsBefore(newLargestIgnored)) {
                record(newLargestIgnored);
            }
        }
    }

    private final QuicTLSEngine quicTLSEngine;
    private final EmittedAckTracker emittedAckTracker;
    private volatile NextAckFrame nextAckFrame; // assigned through VarHandle
    // exponent for outgoing packets; defaults to 3
    public static final int ACK_DELAY_EXPONENT = 3;
    // max ack delay sent in quic transport parameters, in millis
    public static final int ADVERTISED_MAX_ACK_DELAY = 25;
    // max timer delay, i.e. how late selector.select returns; 15.6 millis on Windows
    public static final int TIMER_DELAY = 16;
    // effective max ack delay for outgoing application packets
    public static final int MAX_ACK_DELAY = ADVERTISED_MAX_ACK_DELAY - TIMER_DELAY;

    // exponent for incoming packets
    private volatile long peerAckDelayExponent;
    // max peer ack delay; zero on initial and handshake,
    // initialized from transport parameters on application
    private volatile long peerMaxAckDelayMillis; // ms
    // max ack delay; zero on initial and handshake, MAX_ACK_DELAY on application
    private final long maxAckDelay; // ms
    volatile boolean closed;

    // The last time an ACK eliciting packet was sent.
    // May be null before any such packet is sent...
    private volatile Deadline lastAckElicitingTime;

    // not null if sending ping has been requested.
    private volatile CompletableFuture<Long> pingRequested;

    // The largest packet number successfully processed in this space.
    // Needed to decode received packet numbers, see RFC 9000 appendix A.3
    private volatile long largestProcessedPN; // assigned through VarHandle

    // The largest ACK-eliciting packet number received in this space.
    // Needed to determine if we should send ACK without delay, see RFC 9000 section 13.2.1
    private volatile long largestAckElicitingReceivedPN; // assigned through VarHandle

    // The largest ACK-eliciting packet number sent in this space.
    // Needed to determine if we should arm PTO timer
    private volatile long largestAckElicitingSentPN;

    // The largest packet number acknowledged by peer.
    // Needed to determine packet number length, see RFC 9000 appendix A.2
    private volatile long largestReceivedAckedPN; // assigned through VarHandle

    // The largest packet number acknowledged in this space
    // This is the largest packet number we have acknowledged.
    // This should be less or equal to the largestProcessedPN always.
    // Not used.
    private volatile long largestSentAckedPN; // assigned through VarHandle

    // The largest packet number that this instance has included
    // in an AckFrame sent to the peer, and of which the peer has
    // acknowledged reception.
    // Used to limit ack ranges, see RFC 9000 section 13.2.4
    private volatile long largestAckedPNReceivedByPeer; // assigned through VarHandle

    /**
     * Creates a new {@code PacketSpaceManager} for the given
     * packet number space.
     * @param connection         The connection for which this manager
     *                           is created.
     * @param packetNumberSpace  The packet number space.
     */
    public PacketSpaceManager(final QuicConnectionImpl connection,
                              final PacketNumberSpace packetNumberSpace) {
        this(packetNumberSpace, connection.emitter(), TimeSource.source(),
                connection.rttEstimator, connection.congestionController, connection.getTLSEngine(),
                connection.connectionTerminator(),
                () -> connection.dbgTag() + "[" + packetNumberSpace.name() + "]");
    }

    /**
     * Creates a new {@code PacketSpaceManager} for the given
     * packet number space.
     *
     * @param packetNumberSpace    the packet number space.
     * @param packetEmitter        the packet emitter
     * @param congestionController the congestion controller
     * @param debugStrSupplier     a supplier for a debug tag to use for logging purposes
     */
    public PacketSpaceManager(PacketNumberSpace packetNumberSpace,
                              PacketEmitter packetEmitter,
                              TimeLine instantSource,
                              QuicRttEstimator rttEstimator,
                              QuicCongestionController congestionController,
                              QuicTLSEngine quicTLSEngine,
                              ConnectionTerminator terminator,
                              Supplier<String> debugStrSupplier) {
        largestProcessedPN = largestReceivedAckedPN = largestAckElicitingReceivedPN
                = largestAckElicitingSentPN = largestSentAckedPN = largestAckedPNReceivedByPeer = -1L;
        this.debugStrSupplier = debugStrSupplier;
        this.debug = Utils.getDebugLogger(debugStrSupplier);
        this.instantSource = instantSource;
        this.rttEstimator = rttEstimator;
        this.congestionController = congestionController;
        this.packetNumberSpace = packetNumberSpace;
        this.packetEmitter = packetEmitter;
        this.terminator = terminator;
        this.emittedAckTracker = new EmittedAckTracker();
        this.packetTransmissionTask = new PacketTransmissionTask();
        this.quicTLSEngine = quicTLSEngine;
        maxAckDelay = (packetNumberSpace == PacketNumberSpace.APPLICATION)
                ? MAX_ACK_DELAY : 0;
    }

    /**
     * {@return the max delay before emitting a non ACK-eliciting packet to
     * acknowledge a received ACK-eliciting packet, in milliseconds}
     */
    public long getMaxAckDelay() {
        return maxAckDelay;
    }

    /**
     * {@return the max ACK delay of the peer, in milliseconds}
     */
    public long getPeerMaxAckDelayMillis() {
        return peerMaxAckDelayMillis;
    }

    /**
     * Changes the value of the {@linkplain #getPeerMaxAckDelayMillis()
     * peer max ACK delay} and ack delay exponent
     *
     * @param peerDelay        the new delay, in milliseconds
     * @param ackDelayExponent the new ack delay exponent
     */
    @Override
    public void updatePeerTransportParameters(long peerDelay, long ackDelayExponent) {
        this.peerAckDelayExponent = ackDelayExponent;
        this.peerMaxAckDelayMillis = peerDelay;
    }

    @Override
    public PacketNumberSpace packetNumberSpace() {
        return packetNumberSpace;
    }

    @Override
    public long allocateNextPN() {
        return nextPN.getAndIncrement();
    }

    @Override
    public long getLargestPeerAckedPN() {
        return largestReceivedAckedPN;
    }

    @Override
    public long getLargestProcessedPN() {
        return largestProcessedPN;
    }

    @Override
    public long getMinPNThreshold() {
        return largestAckedPNReceivedByPeer;
    }

    @Override
    public long getLargestSentAckedPN() {
        return largestSentAckedPN;
    }

    /**
     * This method is called by {@link QuicConnectionImpl} upon reception of
     * and successful negotiation of a new version.
     * In that case we should stop retransmitting packet that have the
     * "wrong" version: they will never be acknowledged.
     */
    public void versionChanged() {
        // don't retransmit packet with "bad" version
        assert packetNumberSpace == PacketNumberSpace.INITIAL;
        if (debug.on()) {
            debug.log("version changed - clearing pending acks");
        }
        clearAll();
    }

    public void retry() {
        assert packetNumberSpace == PacketNumberSpace.INITIAL;
        if (debug.on()) {
            debug.log("retry received - clearing pending acks");
        }
        clearAll();
    }

    // adds the PingRequest to the pendingPingRequests queue so
    // that it can be completed when the packet is ACK'ed.
    private void registerPingRequest(PingRequest pingRequest) {
        if (closed) {
            pingRequest.response().completeExceptionally(new IOException("closed"));
            return;
        }
        pendingPingRequests.add(pingRequest);
        // could be acknowledged already!
        processPingResponses(largestReceivedAckedPN);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        if (Log.quicControl() || Log.quicRetransmit()) {
            Log.logQuic("{0} closing packet space {1}",
                    packetEmitter.logTag(), packetNumberSpace);
        }
        if (debug.on()) {
            debug.log("closing packet space");
        }
        // stop the internal scheduler
        packetTransmissionTask.handleScheduler.stop();
        // make sure the task gets eventually removed from the timer
        packetEmitter.reschedule(packetTransmissionTask);
        // clear pending acks, retransmissions
        transferLock.lock();
        try {
            clearAll();
            // discard the (TLS) keys
            if (debug.on()) {
                debug.log("discarding TLS keys");
            }
            this.quicTLSEngine.discardKeys(tlsEncryptionLevel());
        } finally {
            transferLock.unlock();
        }
        rttEstimator.resetPtoBackoff();
        // complete any ping request that hasn't been completed
        IOException io = null;
        try {
            for (var pr : pendingPingRequests) {
                if (io == null) {
                    io = new IOException("Not sending ping because "
                            + this.packetNumberSpace + " packet space is being closed");
                }
                // TODO: is it necessary for this to be an exceptional completion?
                pr.response().completeExceptionally(io);
            }
        } finally {
            pendingPingRequests.clear();
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void runTransmitter() {
        transmitNow = true;
        // run the handle loop
        packetTransmissionTask.handle();
    }

    @Override
    public void packetReceived(PacketType packet, long packetNumber, boolean isAckEliciting) {
        assert PacketNumberSpace.of(packet) == packetNumberSpace;
        assert packetNumber > largestAckedPNReceivedByPeer;

        if (closed) {
            if (debug.on()) {
                debug.log("%s closed, ignoring %s(pn: %s)", packetNumberSpace, packet, packetNumber);
            }
            return;
        }

        if (debug.on()) {
            debug.log("packetReceived %s(pn:%d, needsAck:%s)",
                    packet, packetNumber, isAckEliciting);
        }

        // whether the packet is ack eliciting or not, we need to add its packet
        // number to the ack frame.
        packetProcessed(packetNumber);
        addToAckFrame(packetNumber, isAckEliciting);
    }

    // used in tests
    public <T> T triggeredForRetransmission(Function<LongStream, T> walker) {
        return walker.apply(triggeredForRetransmission.stream()
                .mapToLong(PendingAcknowledgement::packetNumber));
    }

    public <T> T pendingRetransmission(Function<LongStream, T> walker) {
        return walker.apply(pendingRetransmission.stream()
                .mapToLong(PendingAcknowledgement::packetNumber));
    }

    // used in tests
    public <T> T pendingAcknowledgements(Function<LongStream, T> walker) {
        return walker.apply(pendingAcknowledgements.stream()
                .mapToLong(PendingAcknowledgement::packetNumber));
    }

    // used in tests
    public AtomicLong getNextPN() { return nextPN; }

    // Called by the retransmitLoop scheduler.
    // Retransmit one packet for which retransmission has been triggered by
    // the PacketTransmissionTask.
    // return true if something was retransmitted, or false if there was nothing to retransmit
    private boolean retransmit() throws QuicKeyUnavailableException, QuicTransportException {
        PendingAcknowledgement pending;
        final var closed = !this.isOpenForTransmission();
        if (closed) {
            if (debug.on()) {
                debug.log("already closed; retransmission dropped");
            }
            clearAll();
            return false;
        }
        transferLock.lock();
        try {
            pending = triggeredForRetransmission.poll();
        } finally {
            transferLock.unlock();
        }

        if (pending != null) {
            // allocate new packet number
            // create new packet
            // encrypt packet
            // send packet
            if (debug.on()) debug.log("handle: retransmitting...");
            packetEmitter.retransmit(this, pending.packet(), pending.attempts());
            return true;
        }
        return false;
    }

    /**
     * Called by the {@link PacketTransmissionTask} to
     * generate a non ACK eliciting packet containing only the given
     * ACK frame.
     *
     * <p> If a received packet is ACK-eliciting, then it will be either
     * directly acknowledged by {@link QuicConnectionImpl} - which will
     * call {@link #getNextAckFrame(boolean)}  to embed the {@link AckFrame}
     * in a packet, or by a non-eliciting ACK packet which will be
     * triggered {@link #getMaxAckDelay() maxAckDelay} after the reception
     * of the ACK-eliciting packet (this method, triggered by the {@link
     * PacketTransmissionTask}).
     *
     * <p> This method doesn't reset the {@linkplain #getNextAckFrame(boolean)
     * next ack frame} to be sent, but reset its delay so that only
     * one non ACK-eliciting packet is emitted to acknowledge a given
     * packet.
     *
     * @param ackFrame The ACK frame to send.
     * @return the packet number of the emitted packet
     */
    private long emitAckPacket(AckFrame ackFrame, boolean sendPing)
            throws QuicKeyUnavailableException, QuicTransportException {
        final boolean closed = !this.isOpenForTransmission();
        if (closed) {
            if (debug.on()) {
                debug.log("Packet space closed, ack/ping won't be sent"
                        + (ackFrame != null ? ": " + ackFrame : ""));
            }
            return -1L;
        }
        try {
            return packetEmitter.emitAckPacket(this, ackFrame, sendPing);
        } catch (QuicKeyUnavailableException | QuicTransportException e) {
            if (!this.isOpenForTransmission()) {
                // possible race condition where the packet space was closed (and keys discarded)
                // while there was an attempt to send an ACK/PING frame.
                // Ignore such cases, since it's OK to not send those frames when the packet space
                // is already closed
                if (debug.on()) {
                    debug.log("ack/ping wasn't sent since packet space was closed"
                            + (ackFrame != null ? ": " + ackFrame : ""));
                }
                return -1L;
            }
            throw e;
        }
    }

    boolean isClosing(QuicPacket packet) {
        var frames = packet.frames();
        if (frames == null || frames.isEmpty()) return false;
        return frames.stream()
                .anyMatch(ConnectionCloseFrame.class::isInstance);
    }

    private synchronized void lastAckElicitingSent(long packetNumber) {
        if (largestAckElicitingSentPN < packetNumber) {
            largestAckElicitingSentPN = packetNumber;
        }
    }

    @Override
    public void packetSent(QuicPacket packet, long previousPacketNumber, long packetNumber) {
        if (packetNumber < 0) {
            throw new IllegalArgumentException("Invalid packet number: " + packetNumber);
        }
        // RFC-9000, section 10.1: An endpoint also restarts its idle timer when sending
        // an ack-eliciting packet ...
        // TODO: terminator is only null when PacketSpaceManager is constructed in
        // PacketSpaceManagerTest. That test needs a rethink and we shouldn't
        // ever allow terminator to be null.
        if (this.terminator != null && packet.isAckEliciting()) {
            this.terminator.keepAlive();
        }
        largestAckSent(AckFrame.largestAcknowledgedInPacket(packet));
        if (previousPacketNumber >= 0) {
            if (debug.on()) {
                debug.log("retransmitted packet %s(%d) as %d",
                        packet.packetType(), previousPacketNumber, packetNumber);
            }

            boolean found = false;
            transferLock.lock();
            try {
                // check for close and addAcknowledgement in the same lock
                // to avoid races with close / clearAll
                final var closed = !this.isOpenForTransmission();
                if (closed) {
                    if (debug.on()) {
                        debug.log("%s already closed: ignoring packet pn:%s",
                                packetNumberSpace, packet.packetNumber());
                    }
                    return;
                }
                // TODO: should use a tail set here to skip all pending acks
                //       whose packet number is < previousPacketNumber?
                var iterator = pendingRetransmission.iterator();
                PendingAcknowledgement replacement;
                while (iterator.hasNext()) {
                    PendingAcknowledgement pending = iterator.next();
                    if (pending.hasPreviousNumber(previousPacketNumber)) {
                        // no need to retransmit twice, but can this happen?
                        iterator.remove();
                    } else if (!found && pending.hasExactNumber(previousPacketNumber)) {
                        PreviousNumbers previous = new PreviousNumbers(
                                previousPacketNumber,
                                pending.largestAcknowledged, pending.previousNumbers);
                        replacement =
                                new PendingAcknowledgement(packet, now(), packetNumber, previous);
                        if (debug.on()) {
                            debug.log("Packet %s(pn:%s) previous %s(pn:%s) is pending acknowledgement",
                                    packet.packetType(), packetNumber, packet.packetType(), previousPacketNumber);
                        }
                        var rep = replacement;
                        if (lostPackets.removeIf(p -> rep.hasPreviousNumber(p.packetNumber))) {
                            lostPackets.add(rep);
                        }
                        addAcknowledgement(replacement);
                        iterator.remove();
                        found = true;
                    }
                }
            } finally {
                transferLock.unlock();
            }
            if (found && packetTransmissionTask.hasNoDeadline()) {
                packetTransmissionTask.reschedule();
            }
            if (!found) {
                if (debug.on()) {
                    debug.log("packetRetransmitted: packet not found - previous: %s for %s(%s)",
                            previousPacketNumber, packet.packetType(), packetNumber);
                }
            }
        } else {
            if (packet.isAckEliciting()) {
                // This method works with the following assumption:
                // - Non ACK eliciting packet do not need to be retransmitted because:
                //       - they only contain ack frames - which may/will we be retransmitted
                //         anyway with the next ack eliciting packet
                //       - they will not be acknowledged directly - we don't want to
                //         resend them constantly
                if (debug.on()) {
                    debug.log("Packet %s(pn:%s) is pending acknowledgement",
                            packet.packetType(), packetNumber);
                }
                PendingAcknowledgement pending = new PendingAcknowledgement(packet,
                        now(), packetNumber, null);
                transferLock.lock();
                try {
                    // check for close and addAcknowledgement in the same lock
                    // to avoid races with close / clearAll
                    final var closed = !this.isOpenForTransmission();
                    if (closed) {
                        if (debug.on()) {
                            debug.log("%s already closed: ignoring packet pn:%s",
                                    packetNumberSpace, packet.packetNumber());
                        }
                        return;
                    }
                    addAcknowledgement(pending);
                    if (packetTransmissionTask.hasNoDeadline()) {
                        packetTransmissionTask.reschedule();
                    }
                } finally {
                    transferLock.unlock();
                }
            }
        }


    }

    private void addAcknowledgement(PendingAcknowledgement ack) {
        lastAckElicitingSent(ack.sent);
        lastAckElicitingSent(ack.packetNumber);
        pendingAcknowledgements.add(ack);
        sendTimes.put(ack.packetNumber, ack.sent);
        congestionController.packetSent(ack.packet().size());
    }

    /**
     * Computes the next deadline for generating a non ACK eliciting
     * packet containing the next ACK frame, or for retransmitting
     * unacknowledged packets for which retransmission is due.
     * This may be different to the {@link #nextScheduledDeadline()}
     * if newer changes have not been taken into account yet.
     * @return the deadline at which the scheduler's task for this packet
     *         space should be scheduled to wake up
     */
    public Deadline computeNextDeadline() {
        return computeNextDeadline(true);
    }

    public Deadline computeNextDeadline(boolean verbose) {

        if (closed) {
            if (verbose && Log.quicTimer()) {
                Log.logQuic(String.format("%s: [%s] closed - no deadline",
                        packetEmitter.logTag(), packetNumberSpace));
            }
            return Deadline.MAX;
        }
        if (transmitNow) {
            if (verbose && Log.quicTimer()) {
                Log.logQuic(String.format("%s: [%s] transmit now",
                        packetEmitter.logTag(), packetNumberSpace));
            }
            return Deadline.MIN;
        }
        if (pingRequested != null) {
            if (verbose && Log.quicTimer()) {
                Log.logQuic(String.format("%s: [%s] ping requested",
                        packetEmitter.logTag(), packetNumberSpace));
            }
            return Deadline.MIN;
        }
        var ack = nextAckFrame;

        Deadline ackDeadline = (ack == null || ack.sent() != null)
                ? Deadline.MAX // if the ack frame has already been sent, getNextAck() returns null
                : ack.deadline();
        Deadline lossDeadline = getLossTimer();
        // TODO: consider removing the debug traces in this method when integrating
        // if both loss deadline and PTO timer are set, loss deadline is always earlier
        if (verbose && debug.on() && lossDeadline != Deadline.MIN) debug.log("lossDeadline is: " + lossDeadline);
        if (lossDeadline != null) {
            if (verbose && debug.on()) {
                if (lossDeadline == Deadline.MIN) {
                    debug.log("lossDeadline is immediate");
                } else if (!ackDeadline.isBefore(lossDeadline)) {
                    debug.log("lossDeadline in %s ms",
                            Deadline.between(now(), lossDeadline).toMillis());
                } else {
                    debug.log("ackDeadline before lossDeadline in %s ms",
                            Deadline.between(now(), ackDeadline).toMillis());
                }
            }
            if (verbose && Log.quicTimer()) {
                Log.logQuic(String.format("%s: [%s] loss deadline: %s, ackDeadline: %s, deadline in %s",
                    packetEmitter.logTag(), packetNumberSpace, lossDeadline, ackDeadline,
                    Utils.debugDeadline(now(), min(ackDeadline, lossDeadline))));
            }
            return min(ackDeadline, lossDeadline);
        }
        Deadline ptoDeadline = getPtoDeadline();
        if (verbose && debug.on()) debug.log("ptoDeadline is: " + ptoDeadline);
        if (ptoDeadline != null) {
            if (verbose && debug.on()) {
                if (!ackDeadline.isBefore(ptoDeadline)) {
                    debug.log("ptoDeadline in %s ms",
                            Deadline.between(now(), ptoDeadline).toMillis());
                } else {
                    debug.log("ackDeadline before ptoDeadline in %s ms",
                                Deadline.between(now(), ackDeadline).toMillis());
                }
            }
            if (verbose && Log.quicTimer()) {
                Log.logQuic(String.format("%s: [%s] PTO deadline: %s, ackDeadline: %s, deadline in %s",
                    packetEmitter.logTag(), packetNumberSpace, ptoDeadline, ackDeadline,
                    Utils.debugDeadline(now(), min(ackDeadline, ptoDeadline))));
            }
            return min(ackDeadline, ptoDeadline);
        }
        if (verbose && debug.on()) {
            if (ackDeadline == Deadline.MAX) {
                debug.log("ackDeadline is: Deadline.MAX");
            } else {
                debug.log("ackDeadline in %s ms",
                        Deadline.between(now(), ackDeadline).toMillis());
            }
        }
        if (ackDeadline.equals(Deadline.MAX)) {
            if (verbose && Log.quicTimer()) {
                Log.logQuic(String.format("%s: [%s] no deadline: " +
                        "pendingAcks: %s, triggered: %s, pendingRetransmit: %s",
                        packetEmitter.logTag(), packetNumberSpace, pendingAcknowledgements.size(),
                        triggeredForRetransmission.size(), pendingRetransmission.size()));
            }
        } else {
            if (verbose && Log.quicTimer()) {
                Log.logQuic(String.format("%s: [%s] deadline is %s",
                        packetEmitter.logTag(), packetNumberSpace(),
                        Utils.debugDeadline(now(), ackDeadline)));
            }
        }
        return ackDeadline;
    }

    /**
     * {@return the next deadline at which the scheduler's task for this packet
     * space is currently scheduled to wake up}
     */
    public Deadline nextScheduledDeadline() {
        return packetTransmissionTask.nextDeadline;
    }

    private Deadline now() {
        return instantSource.instant();
    }

    /**
     * Tracks the largest packet acknowledged by the packets acknowledged in the
     * given AckFrame. This helps to implement the algorithm described in
     * RFC 9000,  13.2.4. Limiting Ranges by Tracking ACK Frames.
     * @param pending a yet unacknowledged packet that may be acknowledged
     *                by the given{@link AckFrame}.
     * @param frame a received {@code AckFrame}
     * @return whether the given pending unacknowledged packet is being
     *         acknowledged by this ack frame.
     */
    private boolean isAcknowledging(PendingAcknowledgement pending, AckFrame frame) {
        return emittedAckTracker.trackAcknowlegment(pending, frame);
    }

    private boolean isAcknowledgingLostPacket(PendingAcknowledgement pending, AckFrame frame,
                                             List<PendingAcknowledgement>[] recovered) {
        if (frame.isAcknowledging(pending.packetNumber)) {
            if (recovered != null) {
                if (recovered[0] == null) {
                    recovered[0] = new ArrayList<>();
                }
                recovered[0].add(pending);
            }
            return true;
        }
        // There is a potential for a never ending retransmission
        // loop here if we don't treat the ack of a previous packet just
        // as the ack of the tip of the chain.
        // So we call packetEmitter.acknowledged(pending.packet()) here too,
        // and return `true` in this case as well.
        var previous = pending.findPreviousAcknowledged(frame);
        if (previous != null) {
            if (recovered != null) {
                if (recovered[0] == null) {
                    recovered[0] = new ArrayList<>();
                }
                recovered[0].add(pending);
            }
            return true;
        }
        return false;

    }

    @Override
    public void processAckFrame(AckFrame frame) throws QuicTransportException {
        // for each acknowledged packet, remove it from the
        // list of packets pending acknowledgement, or from the
        // list of packets pending retransmission
        long largestAckAckedBefore = emittedAckTracker.largestAckAcked();
        long largestAcknowledged = frame.largestAcknowledged();
        Deadline now = now();
        if (largestAcknowledged >= nextPN.get()) {
            throw new QuicTransportException("Acknowledgement for a nonexistent packet",
                    null, frame.getTypeField(), QuicTransportErrors.PROTOCOL_VIOLATION);
        }

        int lostCount;
        transferLock.lock();
        try {
            if (largestAckReceived(largestAcknowledged)) {
                // if the largest acknowledged PN is newly acknowledged
                // and at least one of the newly acked packets is ack-eliciting
                // -> use the new RTT sample
                // the below code only checks if largest acknowledged is ack-eliciting
                Deadline sentTime = sendTimes.get(largestAcknowledged);
                if (sentTime != null) {
                    long ackDelayMicros;
                    if (isApplicationSpace()) {
                        confirmHandshake();
                        long baseAckDelay = peerAckDelayToMicros(frame.ackDelay());
                        // if packet was sent after handshake confirmed, use max ack delay
                        if (largestAcknowledged >= handshakeConfirmedPN) {
                            ackDelayMicros = Math.min(
                                    baseAckDelay,
                                    TimeUnit.MILLISECONDS.toMicros(peerMaxAckDelayMillis));
                        } else {
                            ackDelayMicros = baseAckDelay;
                        }
                    } else {
                        // acks are not delayed during handshake
                        ackDelayMicros = 0;
                    }
                    long rttSample = sentTime.until(now, ChronoUnit.MICROS);
                    if (debug.on()) {
                        debug.log("New RTT sample on packet %s: %s us (delay %s us)",
                                largestAcknowledged, rttSample,
                                ackDelayMicros);
                    }
                    rttEstimator.consumeRttSample(
                            rttSample,
                            ackDelayMicros,
                            now
                    );
                } else {
                    if (debug.on()) {
                        debug.log("RTT sample on packet %s ignored: not ack eliciting",
                                largestAcknowledged);
                    }
                }
                if (packetNumberSpace != PacketNumberSpace.INITIAL) {
                    rttEstimator.resetPtoBackoff();
                }
                purgeSendTimes(largestAcknowledged);
                // complete PingRequests if needed
                processPingResponses(largestAcknowledged);
            } else {
                if (debug.on()) {
                    debug.log("RTT sample on packet %s ignored: not largest",
                            largestAcknowledged);
                }
            }

            pendingRetransmission.removeIf((p) -> isAcknowledging(p, frame));
            triggeredForRetransmission.removeIf((p) -> isAcknowledging(p, frame));
            for (Iterator<PendingAcknowledgement> iterator = pendingAcknowledgements.iterator(); iterator.hasNext(); ) {
                PendingAcknowledgement p = iterator.next();
                if (isAcknowledging(p, frame)) {
                    iterator.remove();
                    congestionController.packetAcked(p.packet.size(), p.sent);
                }
            }
            lostCount = detectAndRemoveLostPackets(now);
            @SuppressWarnings({"unchecked","rawtypes"})
            List<PendingAcknowledgement>[] recovered= Log.quicRetransmit() ? new List[1] : null;
            lostPackets.removeIf((p) -> isAcknowledgingLostPacket(p, frame, recovered));
            if (recovered != null && recovered[0] != null) {
                Log.logQuic("{0} lost packets recovered: {1}({2})  total unrecovered {3}, unacknowledged {4}",
                        packetEmitter.logTag(), packetType(),
                        recovered[0].stream().map(PendingAcknowledgement::packetNumber).toList(),
                        lostPackets.size(), pendingAcknowledgements.size() + pendingRetransmission.size());
            }
         } finally {
            transferLock.unlock();
        }

        long largestAckAcked = emittedAckTracker.largestAckAcked();
        if (largestAckAcked > largestAckAckedBefore) {
            if (debug.on()) {
                debug.log("%s: largestAckAcked=%d - cleaning up AckFrame",
                        packetNumberSpace, largestAckAcked);
            }
            // remove ack ranges that we no longer need to acknowledge.
            // this implements the algorithm described in RFC 9000,
            // 13.2.4. Limiting Ranges by Tracking ACK Frames
            cleanupAcks();
        }

        if (lostCount > 0) {
            if (debug.on())
                debug.log("Found %s lost packets", lostCount);
            // retransmit if possible
            runTransmitter();
        } else if (blockedByCC && congestionController.canSendPacket()) {
            // CC just got unblocked... send more data
            blockedByCC = false;
            runTransmitter();
        } else {
            // RTT was updated, some packets might be lost, recompute timers
            packetTransmissionTask.reschedule();
        }
    }

    @Override
    public void confirmHandshake() {
        assert isApplicationSpace();
        if (handshakeConfirmedPN == 0) {
            handshakeConfirmedPN = nextPN.get();
        }
    }

    private void purgeSendTimes(long largestAcknowledged) {
        sendTimes.headMap(largestAcknowledged, true).clear();
    }

    private long peerAckDelayToMicros(long ackDelay) {
        return ackDelay << peerAckDelayExponent;
    }

    private NextAckFrame getNextAck(boolean onlyOverdue, int maxSize) {
        Deadline now = now();
        // This method is called to retrieve the AckFrame that will
        // be embedded in the next packet sent to the peer.
        // We therefore need to disarm the timer that will send a
        // non-ACK eliciting packet with that AckFrame (if any) before
        // returning the AckFrame. This is the purpose of the loop
        // below...
        while (true) {
            NextAckFrame ack = nextAckFrame;
            if (ack == null
                    || ack.deadline() == Deadline.MAX
                    || (onlyOverdue && ack.deadline().isAfter(now))
                    || ack.sent() != null) {
                return null;
            }
            // also reserve 3 bytes for the ack delay
            if (ack.ackFrame().size() > maxSize - 3) return null;
            NextAckFrame newAck = ack.withDeadline(Deadline.MAX, now);
            boolean respin = !Handles.NEXTACK.compareAndSet(this, ack, newAck);
            if (!respin) {
                return ack;
            }
        }
    }

    @Override
    public AckFrame getNextAckFrame(boolean onlyOverdue) {
        return getNextAckFrame(onlyOverdue, Integer.MAX_VALUE);
    }

    @Override
    public AckFrame getNextAckFrame(boolean onlyOverdue, int maxSize) {
        if (closed) {
            return null;
        }
        NextAckFrame ack = getNextAck(onlyOverdue, maxSize);
        if (ack == null) {
            return null;
        }
        long delay = ack.lastUpdated()
                .until(now(), ChronoUnit.MICROS) >> ACK_DELAY_EXPONENT;
        return ack.ackFrame().withAckDelay(delay);
    }

    /**
     * Returns the count of unacknowledged packets that were declared lost.
     * The lost packets are moved from the pendingAcknowledgements
     * into the pendingRetransmission.
     *
     * @param now current time, used for time-based loss detection.
     */
    private int detectAndRemoveLostPackets(Deadline now) {
        Deadline lossSendTime = now.minus(rttEstimator.getLossThreshold());
        int count = 0;
        // debug.log("preparing for retransmission");
        transferLock.lock();
        try {
            List<PendingAcknowledgement> lost = Log.quicRetransmit() ? new ArrayList<>() : null;
            List<QuicPacket> packets = new ArrayList<>();
            Deadline firstSendTime = null, lastSendTime = null;
            for (PendingAcknowledgement head = pendingAcknowledgements.peek();
                 head != null && head.packetNumber < largestReceivedAckedPN;
                 head = pendingAcknowledgements.peek()) {
                if (head.packetNumber < largestReceivedAckedPN - kPacketThreshold ||
                        !lossSendTime.isBefore(head.sent)) {
                    if (debug.on()) {
                        debug.log("retransmit:head pn:" + head.packetNumber +
                                ",largest acked PN:" + largestReceivedAckedPN +
                                ",sent:" + head.sent +
                                ",lossSendTime:" + lossSendTime
                        );
                    }
                    if (pendingAcknowledgements.remove(head)) {
                        pendingRetransmission.add(head);
                        triggeredForRetransmission.add(head);
                        packets.add(head.packet);
                        if (firstSendTime == null) {
                            firstSendTime = head.sent;
                        }
                        lastSendTime = head.sent;
                        var lp = head;
                        lostPackets.removeIf(p -> lp.hasPreviousNumber(p.packetNumber));
                        lostPackets.add(head);
                        count++;
                        if (lost != null) lost.add(head);
                    }
                } else {
                    if (debug.on()) {
                        debug.log("no retransmit:head pn:" + head.packetNumber +
                                ",largest acked PN:" + largestReceivedAckedPN +
                                ",sent:" + head.sent +
                                ",lossSendTime:" + lossSendTime
                        );
                    }
                    break;
                }
            }
            if (!packets.isEmpty()) {
                // Persistent congestion is detected more aggressively than mandated by RFC 9002:
                // - may be reported even if there's no prior RTT sample
                // - may be reported even if there are acknowledged packets between the lost ones
                boolean persistent = Deadline.between(firstSendTime, lastSendTime)
                                .compareTo(getPersistentCongestionDuration()) > 0;
                congestionController.packetLost(packets, lastSendTime, persistent);
            }
            if (lost != null && !lost.isEmpty()) {
                Log.logQuic("{0} lost packet {1}({2}) total unrecovered {3}, unacknowledged {4}",
                        packetEmitter.logTag(),
                         packetType(), lost.stream().map(PendingAcknowledgement::packetNumber).toList(),
                        lostPackets.size(), pendingAcknowledgements.size() + pendingRetransmission.size());
            }
        } finally {
            transferLock.unlock();
        }
        return count;
    }

    PacketType packetType() {
        return switch (packetNumberSpace) {
            case INITIAL -> PacketType.INITIAL;
            case HANDSHAKE -> PacketType.HANDSHAKE;
            case APPLICATION -> PacketType.ONERTT;
            case NONE -> PacketType.NONE;
        };
    }

    /**
     * {@return true if PTO timer expired, false otherwise}
     */
    private boolean isPTO(Deadline now) {
        Deadline ptoDeadline = getPtoDeadline();
        return ptoDeadline != null && !ptoDeadline.isAfter(now);
    }

    // returns true if this space is the APPLICATION space
    private boolean isApplicationSpace() {
        return packetNumberSpace == PacketNumberSpace.APPLICATION;
    }

    // returns the PTO duration
    Duration getPtoDuration() {
        var pto = rttEstimator.getBasePtoDuration()
                .plusMillis(peerMaxAckDelayMillis)
                .multipliedBy(rttEstimator.getPtoBackoff());
        var max = QuicRttEstimator.MAX_PTO_BACKOFF_TIMEOUT;
        // don't allow PTO > 240s
        return pto.compareTo(max) > 0 ? max : pto;
    }

    // returns the persistent congestion duration
    Duration getPersistentCongestionDuration() {
        return rttEstimator.getBasePtoDuration()
                .plusMillis(peerMaxAckDelayMillis)
                .multipliedBy(kPersistentCongestionThreshold);
    }

    private Deadline getPtoDeadline() {
        if (packetNumberSpace == PacketNumberSpace.INITIAL && lastAckElicitingTime != null) {
            if (!quicTLSEngine.keysAvailable(QuicTLSEngine.KeySpace.HANDSHAKE)) {
                // if handshake keys are not available, initial PTO must be set
                return lastAckElicitingTime.plus(getPtoDuration());
            }
        }
        if (packetNumberSpace == PacketNumberSpace.HANDSHAKE) {
            // set anti-deadlock timer
            if (lastAckElicitingTime == null) {
                lastAckElicitingTime = now();
            }
            if (largestAckElicitingSentPN == -1) {
                return lastAckElicitingTime.plus(getPtoDuration());
            }
        }
        if (largestAckElicitingSentPN <= largestReceivedAckedPN) {
            return null;
        }
        // Application space deadline can only be set when handshake is confirmed
        if (isApplicationSpace() && quicTLSEngine.getHandshakeState() != QuicTLSEngine.HandshakeState.HANDSHAKE_CONFIRMED) {
            return null;
        }
        return lastAckElicitingTime.plus(getPtoDuration());
    }

    private Deadline getLossTimer() {
        PendingAcknowledgement head = pendingAcknowledgements.peek();
        if (head == null || head.packetNumber >= largestReceivedAckedPN) {
            return null;
        }
        if (head.packetNumber < largestReceivedAckedPN - kPacketThreshold) {
            return Deadline.MIN;
        }
        return head.sent.plus(rttEstimator.getLossThreshold());
    }

    // Compute the new deadline when adding an ack-eliciting packet number
    // to an ack frame which is not empty.
    private Deadline computeNewDeadlineFor(AckFrame frame, Deadline now, Deadline deadline,
                                          long packetNumber, long previousLargest,
                                          long ackDelay) {

        boolean previousEliciting = !deadline.equals(Deadline.MAX);

        if (closed) return Deadline.MAX;

        if (previousEliciting) {
            // RFC 9000 #13.2.2:
            // We should send an ACK immediately after receiving two
            // ACK-eliciting packets
            if (debug.on()) {
                debug.log("two ACK-Eliciting packets received: " +
                        "next ack deadline now");
            }
            return now;
        } else if (packetNumber < previousLargest) {
            // RFC 9000 #13.2.1:
            // if the packet has PN less than another ack-eliciting packet,
            // send ACK frame as soon as possible
            if (debug.on()) {
                debug.log("ACK-Eliciting packet received out of order: " +
                        "next ack deadline now");
            }
            return now;
        } else if (packetNumber - 1 > previousLargest && previousLargest > -1) {
            // RFC 9000 #13.2.1:
            // Check whether there are gaps between this packet and the
            // previous ACK-eliciting packet that was received:
            // if we find any gap we should send an ACK frame as soon
            // as possible
            if (!frame.isRangeAcknowledged(previousLargest + 1, packetNumber)) {
                if (debug.on()) {
                    debug.log("gaps detected between this packet" +
                            " and the previous ACK eliciting packet: " +
                            "next ack deadline now");
                }
                return now;
            }
        }
        // send ACK within max delay
        return now.plusMillis(ackDelay);
    }

    /**
     * Used to request sending of a ping frame, for instance, to verify that
     * the connection is alive.
     * @return a completable future that will be completed with the time it
     *   took, in milliseconds, for the peer to acknowledge the packet that
     *   contained the PingFrame (or any packet that was sent after)
     */
    @Override
    public CompletableFuture<Long> requestSendPing() {
        CompletableFuture<Long> pingRequested;
        synchronized (this) {
            if ((pingRequested = this.pingRequested) == null) {
                pingRequested = this.pingRequested = new MinimalFuture<>();
            }
        }
        runTransmitter();
        return pingRequested;
    }

    // Look at whether a ping frame should be sent with the
    // next ACK frame...
    // If a PING frame should be sent, return the new deadline (now)
    // Otherwise, return Deadline.MAX;
    // A PING frame will be sent if:
    //     - the AckFrame contains more than (10) ACK Ranges
    //     - and no ACK eliciting packet was sent, or the last ACK-eliciting was
    //       sent long enough ago - typically 1 PTO delay
    // These numbers are implementation dependent and not defined in the RFC, but
    // help implement a strategy that sends occasional PING frames to limit the size
    // of the ACK frames - as described in RFC 9000.
    //
    // See RFC 9000 Section 13.2.4
    private boolean shouldSendPing(Deadline now, AckFrame frame) {
        Deadline last = lastAckElicitingTime;
        if (frame != null &&
                (last == null ||
                last.isBefore(now.minus(rttEstimator.getBasePtoDuration())))
                && frame.ackRanges().size() > MAX_ACKRANGE_COUNT_BEFORE_PING) {
            return true;
        }
        return false;
    }

    // TODO: store the builder instead of storing the AckFrame?
    //       storing a builder would mean locking - so it might not be a good
    //       idea. But creating a new builder and AckFrame each time means
    //       producing more garbage for the GC to collect.
    // This method is called when a new packet is received, and it adds the
    // received packet number to the next ACK frame to send out.
    // If the packet is ACK eliciting it also arms a timeout (if needed)
    // to make sure the packet will be acknowledged within the committed
    // time frame.
    private void addToAckFrame(long packetNumber, boolean isAckEliciting) {

        long largestAckEliciting = largestAckElicitingReceivedPN;
        if (isAckEliciting) ackElicitingPacketProcessed(packetNumber);

        if (debug.on()) {
            if (packetNumber < largestAckEliciting) {
                debug.log("already received a larger ACK eliciting packet");
            }
        }

        // compute a new AckFrame that includes the
        // provided packet number
        NextAckFrame nextAckFrame, ack = null;
        boolean reschedule;
        long largestAckAcked;
        long newLargestAckAcked = -1;
        do {
            Deadline now = now();
            nextAckFrame = this.nextAckFrame;
            var frame = nextAckFrame == null ? null : nextAckFrame.ackFrame();
            largestAckAcked = emittedAckTracker.largestAckAcked();
            boolean needNewFrame = (frame == null || !frame.isAcknowledging(packetNumber))
                        && packetNumber > largestAckAcked;
            if (needNewFrame) {
                if (debug.on()) {
                    debug.log("Adding %s(%d) to ackFrame %s (ackEliciting %s)",
                            packetNumberSpace, packetNumber, nextAckFrame, isAckEliciting);
                }
                var builder = AckFrameBuilder
                        .ofNullable(frame)
                        .dropAcksBefore(largestAckAcked)
                        .addAck(packetNumber);
                assert !builder.isEmpty();
                frame = builder.build();

                // Note: we could optimize this if needed by simply using a max number of
                // ranges: we could pre-compute the approximate size of a frame that has N ranges
                // and use that.
                final int maxFrameSize = QuicConnectionImpl.SMALLEST_MAXIMUM_DATAGRAM_SIZE - 100;
                if (frame.size() > maxFrameSize) {
                    // frame is too big. We will drop some ranges
                    int ranges = frame.ackRanges().size();
                    int index = ranges/3;
                    builder.dropAckRangesAfter(index);
                    newLargestAckAcked = builder.getLargestAckAcked();
                    var newFrame = builder.build();
                    if (Log.quicCC() || Log.quicRetransmit()) {
                        Log.logQuic("{0}: frame too big ({1} bytes) dropping ack ranges after {2}, " +
                                "will ignore packets smaller than {3} (new frame: {4} bytes)",
                                debugStrSupplier.get(), Integer.toString(frame.size()),
                                Integer.toString(index), Long.toString(newLargestAckAcked),
                                Integer.toString(newFrame.size()));
                    }
                    frame = newFrame;
                    assert frame.size() <= maxFrameSize;
                }
                assert frame.isAcknowledging(packetNumber);
                if (nextAckFrame == null) {
                    if (debug.on()) debug.log("no previous ackframe");
                    Deadline deadline = isAckEliciting
                            ? now.plusMillis(maxAckDelay)
                            : Deadline.MAX;
                    ack = new NextAckFrame(frame, deadline, now, null);
                    reschedule = isAckEliciting;
                    if (debug.on()) debug.log("next deadline: " + maxAckDelay);
                } else {
                    Deadline deadline = nextAckFrame.deadline();
                    Deadline nextDeadline = deadline;
                    boolean deadlineNotExpired = now.isBefore(deadline);
                    if (isAckEliciting && deadlineNotExpired) {
                        if (debug.on()) debug.log("computing new deadline for ackframe");
                        nextDeadline = computeNewDeadlineFor(frame, now, deadline,
                                packetNumber, largestAckEliciting, maxAckDelay);
                    }
                    long millisToNext = nextDeadline.equals(Deadline.MAX)
                            ? Long.MAX_VALUE
                            : now.until(nextDeadline, ChronoUnit.MILLIS);
                    if (debug.on()) {
                        if (nextDeadline == Deadline.MAX) {
                            debug.log("next deadline is: Deadline.MAX");
                        } else {
                            debug.log("next deadline is: " + millisToNext);
                        }
                    }
                    ack = new NextAckFrame(frame, nextDeadline, now, null);
                    reschedule = !nextDeadline.equals(deadline)
                            || millisToNext <= 0;
                }
                if (debug.on()) {
                    String delay = reschedule ? Utils.millis(now(), ack.deadline())
                            : "not rescheduled";
                    debug.log("%s: new ackFrame composed: %s - reschedule=%s",
                            packetNumberSpace, ack.ackFrame(), delay);
                }
            } else {
                reschedule = false;
                if (debug.on()) {
                    debug.log("packet %s(%d) is already in ackFrame %s",
                            packetNumberSpace, packetNumber, nextAckFrame);
                }
                break;
            }
        } while (!Handles.NEXTACK.compareAndSet(this, nextAckFrame, ack));

        if (newLargestAckAcked >= 0) {
            // we reduced the frame because it was too big: we need to ignore
            // packets that are larger than the new largest ignored packet.
            // this is now our new de-facto 'largestAckAcked' even if it wasn't
            // really acked by the peer
            emittedAckTracker.dropPacketNumbersSmallerThan(newLargestAckAcked);
        }

        var ackFrame = ack == null ? null : ack.ackFrame();
        assert packetNumber <= largestAckAcked
                || ackFrame != null && ackFrame.isAcknowledging(packetNumber)
                || nextAckFrame != null && nextAckFrame.ackFrame() != null
                         && nextAckFrame.ackFrame.isAcknowledging(packetNumber)
                : "packet %s(%s) should be in ackFrame"
                .formatted(packetNumberSpace, packetNumber);

        if (reschedule) {
            runTransmitter();
        }
    }

    void debugState() {
        if (debug.on()) {
            debug.log("state: %s", isClosed() ? "closed" : "opened" );
            debug.log("AckFrame: " + nextAckFrame);
            String pendingAcks = pendingAcknowledgements.stream()
                    .map(PendingAcknowledgement::prettyPrint)
                    .collect(Collectors.joining(", ", "(", ")"));
            String pendingRetransmit = pendingRetransmission.stream()
                    .map(PendingAcknowledgement::prettyPrint)
                    .collect(Collectors.joining(", ", "(", ")"));
            debug.log("Pending acks: %s", pendingAcks);
            debug.log("Pending retransmit: %s", pendingRetransmit);
        }
    }

    void debugState(String prefix, StringBuilder sb) {
        String state = isClosed() ? "closed" : "opened";
        sb.append(prefix).append("State: ").append(state).append('\n');
        sb.append(prefix).append("AckFrame: ").append(nextAckFrame).append('\n');
        String pendingAcks = pendingAcknowledgements.stream()
                .map(PendingAcknowledgement::prettyPrint)
                .collect(Collectors.joining(", ", "(", ")"));
        String pendingRetransmit = pendingRetransmission.stream()
                .map(PendingAcknowledgement::prettyPrint)
                .collect(Collectors.joining(", ", "(", ")"));
        sb.append(prefix).append("Pending acks: ").append(pendingAcks).append('\n');
        sb.append(prefix).append("Pending retransmit: ").append(pendingRetransmit);
    }

    @Override
    public boolean isAcknowledged(long packetNumber) {
        var ack = nextAckFrame;
        var ackFrame = ack == null ? null : ack.ackFrame();
        var largestProcessed = largestProcessedPN;
        // if ackFrame is null it means all packets <= largestProcessedPN
        // have been acked.
        if (ackFrame == null) return packetNumber <= largestProcessed;
        if (packetNumber > largestProcessed) return false;
        var largestAckedPNReceivedByPeer = this.largestAckedPNReceivedByPeer;
        if (packetNumber <= largestAckedPNReceivedByPeer) return true;
        return ackFrame.isAcknowledging(packetNumber);
    }

    @Override
    public void fastRetransmit() {
        assert packetNumberSpace == PacketNumberSpace.INITIAL;
        if (closed || fastRetransmitDone) {
            return;
        }
        fastRetransmit = true;
        if (Log.quicControl() || Log.quicRetransmit()) {
            Log.logQuic("Scheduling fast retransmit");
        } else if (debug.on()) {
            debug.log("Scheduling fast retransmit");
        }
        runTransmitter();

    }

    private static Deadline min(Deadline one, Deadline two) {
        return two.isAfter(one) ? one : two;
    }

    // This implements the algorithm described in RFC 9000:
    // 13.2.4. Limiting Ranges by Tracking ACK Frames
    private void cleanupAcks() {
        // clean up the next ACK frame, removing all packets <= largestAckAcked
        NextAckFrame nextAckFrame, ack = null;
        long largestAckAcked;
        do {
            nextAckFrame = this.nextAckFrame;
            if (nextAckFrame == null) return; // nothing to do!
            var frame = nextAckFrame.ackFrame();
            largestAckAcked = emittedAckTracker.largestAckAcked();
            boolean needNewFrame = frame != null
                    && frame.smallestAcknowledged() <= largestAckAcked;
            if (needNewFrame) {
                if (debug.on()) {
                    debug.log("Dropping all acks below %s(%d) in ackFrame %s",
                            packetNumberSpace, largestAckAcked, nextAckFrame);
                }
                var builder = AckFrameBuilder
                        .ofNullable(frame)
                        .dropAcksBefore(largestAckAcked);
                frame = builder.isEmpty() ? null : builder.build();
                if (frame == null) {
                    ack = null;
                    if (debug.on()) {
                        debug.log("%s: ackFrame cleared - nothing to acknowledge",
                                packetNumberSpace);
                    }
                } else {
                    Deadline deadline = nextAckFrame.deadline();
                    ack = new NextAckFrame(frame, deadline,
                            nextAckFrame.lastUpdated(), nextAckFrame.sent());
                    if (debug.on()) {
                        debug.log("%s: ackFrame cleaned up: %s",
                                packetNumberSpace, ack.ackFrame());
                    }
                }
            } else {
                if (debug.on()) {
                    debug.log("%s: no packet smaller than %d in ackFrame %s",
                            packetNumberSpace, largestAckAcked, nextAckFrame);
                }
                break;
            }
        } while (!Handles.NEXTACK.compareAndSet(this, nextAckFrame, ack));

        var ackFrame = ack == null ? null : ack.ackFrame();
        assert ackFrame == null || ackFrame.smallestAcknowledged() > largestAckAcked
                : "%s(pn > %s) should not acknowledge packet <= %s"
                .formatted(packetNumberSpace, ackFrame.smallestAcknowledged(), largestAckAcked);
    }

    private long ackElicitingPacketProcessed(long packetNumber) {
        long largestPN;
        do {
            largestPN = largestAckElicitingReceivedPN;
            if (largestPN >= packetNumber) return largestPN;
        } while (!Handles.LARGEST_ACK_ELICITING_RECEIVED_PN
                .compareAndSet(this, largestPN, packetNumber));
        return packetNumber;
    }

    private long packetProcessed(long packetNumber) {
        long largestPN;
        do {
            largestPN = largestProcessedPN;
            if (largestPN >= packetNumber) return largestPN;
        } while (!Handles.LARGEST_PROCESSED_PN
                .compareAndSet(this, largestPN, packetNumber));
        return packetNumber;
    }

    /**
     * Theoretically we should wait for the packet that contains the
     * ping frame to be acknowledged, but if we receive the ack of a
     * packet with a larger number, we can assume that the connection
     * is still alive, and therefore complete the ping response.
     * @param packetNumber the acknowledged packet number
     */
    private void processPingResponses(long packetNumber) {
        if (pendingPingRequests.isEmpty()) return;
        var iterator = pendingPingRequests.iterator();
        while (iterator.hasNext()) {
            var pr = iterator.next();
            if (pr.packetNumber() <= packetNumber) {
                iterator.remove();
                pr.response().complete(pr.sent().until(now(), ChronoUnit.MILLIS));
            } else {
                // this is a queue, so the PingRequest with the smaller
                // packet number will be at the head. We can stop iterating
                // as soon as we find a PingRequest that has a packet
                // number larger than the one acknowledged.
                break;
            }
        }
    }

    private long largestAckSent(long packetNumber) {
        long largestPN;
        do {
            largestPN = largestSentAckedPN;
            if (largestPN >= packetNumber) return largestPN;
        } while (!Handles.LARGEST_SENT_ACKED_PN
                .compareAndSet(this, largestPN, packetNumber));
        return packetNumber;
    }

    private boolean largestAckReceived(long packetNumber) {
        long largestPN;
        do {
            largestPN = largestReceivedAckedPN;
            if (largestPN >= packetNumber) return false; // already up to date
        } while (!Handles.LARGEST_RECEIVED_ACKED_PN
                .compareAndSet(this, largestPN, packetNumber));
        return true; // updated
    }

    // records the time at which the last ACK-eliciting packet was sent.
    // This has the side effect of resetting the nextPingTime to Deadline.MAX
    // The logic is that a PING frame only need to be sent if no ACK-eliciting
    // packet has been sent for some time (and the AckFrame has grown big enough).
    // See RFC 9000 - Section 13.2.4
    private Deadline lastAckElicitingSent(Deadline now) {
        Deadline max;
        if (debug.on())
            debug.log("Updating last send time to %s", now);
        do {
            max = lastAckElicitingTime;
            if (max != null && !now.isAfter(max)) return max;
        } while (!Handles.LAST_ACK_ELICITING_TIME
                .compareAndSet(this, max, now));
        return now;
    }

    /**
     * returns the TLS encryption level of this packet space as specified
     * in RFC-9001, section 4, table 1.
     */
    private QuicTLSEngine.KeySpace tlsEncryptionLevel() {
        return switch (this.packetNumberSpace) {
            case INITIAL -> QuicTLSEngine.KeySpace.INITIAL;
            // APPLICATION packet space could even mean 0-RTT, but currently we don't support 0-RTT
            case APPLICATION -> QuicTLSEngine.KeySpace.ONE_RTT;
            case HANDSHAKE -> QuicTLSEngine.KeySpace.HANDSHAKE;
            default -> throw new IllegalStateException("No known TLS encryption level" +
                    " for packet space: " + this.packetNumberSpace);
        };
    }

    // VarHandle provide the same atomic compareAndSet functionality
    // than AtomicXXXXX classes, but without the additional cost in
    // footprint.
    private static final class Handles {
        private Handles() {throw new InternalError();}
        static final VarHandle DEADLINE;
        static final VarHandle NEXTACK;
        static final VarHandle LARGEST_PROCESSED_PN;
        static final VarHandle LARGEST_ACK_ELICITING_RECEIVED_PN;
        static final VarHandle LARGEST_RECEIVED_ACKED_PN;
        static final VarHandle LARGEST_SENT_ACKED_PN;
        static final VarHandle LARGEST_ACK_ACKED_PN;
        static final VarHandle LAST_ACK_ELICITING_TIME;
        static final VarHandle IGNORE_ALL_PN_BEFORE;
        static {
            Lookup lookup = MethodHandles.lookup();
            try {
                Class<?> srt = PacketTransmissionTask.class;
                DEADLINE = lookup.findVarHandle(srt, "nextDeadline", Deadline.class);

                Class<?> pmc = PacketSpaceManager.class;
                LAST_ACK_ELICITING_TIME = lookup.findVarHandle(pmc,
                        "lastAckElicitingTime", Deadline.class);
                NEXTACK = lookup.findVarHandle(pmc, "nextAckFrame", NextAckFrame.class);
                LARGEST_RECEIVED_ACKED_PN = lookup
                        .findVarHandle(pmc, "largestReceivedAckedPN", long.class);
                LARGEST_SENT_ACKED_PN = lookup
                        .findVarHandle(pmc, "largestSentAckedPN", long.class);
                LARGEST_PROCESSED_PN = lookup
                        .findVarHandle(pmc, "largestProcessedPN", long.class);
                LARGEST_ACK_ELICITING_RECEIVED_PN = lookup
                        .findVarHandle(pmc, "largestAckElicitingReceivedPN", long.class);
                LARGEST_ACK_ACKED_PN = lookup
                        .findVarHandle(pmc, "largestAckedPNReceivedByPeer", long.class);

                Class<?> eat = EmittedAckTracker.class;
                IGNORE_ALL_PN_BEFORE = lookup
                        .findVarHandle(eat, "ignoreAllPacketsBefore", long.class);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);

            }
        }
    }

    static final class OneRttPacketSpaceManager extends PacketSpaceManager
            implements QuicOneRttContext {

        OneRttPacketSpaceManager(final QuicConnectionImpl connection) {
            super(connection, PacketNumberSpace.APPLICATION);
        }
    }

    static final class HandshakePacketSpaceManager extends PacketSpaceManager {
        private final PacketSpaceManager initialPktSpaceMgr;
        private final boolean isClientConnection;
        private final AtomicBoolean firstPktSent = new AtomicBoolean();

        HandshakePacketSpaceManager(final QuicConnectionImpl connection,
                                    final PacketSpaceManager initialPktSpaceManager) {
            super(connection, PacketNumberSpace.HANDSHAKE);
            this.isClientConnection = connection.isClientConnection();
            this.initialPktSpaceMgr = initialPktSpaceManager;
        }

        @Override
        public void packetSent(QuicPacket packet, long previousPacketNumber, long packetNumber) {
            super.packetSent(packet, previousPacketNumber, packetNumber);
            if (!isClientConnection) {
                // nothing additional to be done for server connections
                return;
            }
            if (firstPktSent.compareAndSet(false, true)) {
                // if this is the first packet we sent in the HANDSHAKE keyspace
                // then we close the INITIAL space discard the INITIAL keys.
                // RFC-9000, section 17.2.2.1:
                // A client stops both sending and processing Initial packets when it sends
                // its first Handshake packet. ... Though packets might still be in flight or
                // awaiting acknowledgment, no further Initial packets need to be exchanged
                // beyond this point. Initial packet protection keys are discarded along with
                // any loss recovery and congestion control state
                if (debug.on()) {
                    debug.log("first handshake packet sent by client, initiating close of" +
                            " INITIAL packet space");
                }
                this.initialPktSpaceMgr.close();
            }
        }
    }
}
