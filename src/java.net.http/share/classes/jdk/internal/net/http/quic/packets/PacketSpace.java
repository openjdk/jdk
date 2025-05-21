/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.packets;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.frames.AckFrame;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketType;
import jdk.internal.net.http.quic.PacketSpaceManager;
import jdk.internal.net.quic.QuicTransportException;

/**
 * An interface implemented by classes which keep track of packet
 * numbers for a given packet number space.
 */
public sealed interface PacketSpace permits PacketSpaceManager {

    /**
     * called on application packet space to record peer's transport parameters
     * @param peerDelay        max_ack_delay
     * @param ackDelayExponent ack_delay_exponent
     */
    void updatePeerTransportParameters(long peerDelay, long ackDelayExponent);

    /**
     * {@return the packet number space managed by this class}
     */
    PacketNumberSpace packetNumberSpace();

    /**
     * The largest processed PN is used to compute
     * the packet number of an incoming Quic packet.
     *
     * @return the largest incoming packet number that
     * was successfully processed in this space.
     */
     long getLargestProcessedPN();

    /**
     * The largest received acked PN is used to compute the
     * packet number that we include in an outgoing Quic packet.
     *
     * @return the largest packet number that was acknowledged by
     * the peer in this space.
     */
     long getLargestPeerAckedPN();

    /**
     * {@return the largest packet number that we have acknowledged in this
     * space}
     *
     * @apiNote This is necessarily greater or equal to the packet number
     * returned by {@linkplain #getMinPNThreshold()}.
     */
    long getLargestSentAckedPN();

    /**
     * {@return the packet number threshold below which packets should be
     * discarded without being processed in this space}
     *
     * @apiNote
     * This corresponds to the largest acknowledged packet number
     * carried in an outgoing ACK frame whose packet number has
     * been acknowledged by the peer. In other words, the largest
     * packet number sent by the peer for which we know that the
     * peer has received an acknowledgement.
     * <p>
     * Note that we need to track the ACK of outgoing packets that
     * contain ACK frames in order to figure out whether a peer
     * knows that a particular packet number has been received and
     * avoid retransmission. However - we don't want ACK frames to grow
     * too big and therefore we can drop some of the information,
     * based on the largestSentAckedPN - see RFC 9000 Section 13.2
     *
     */
    long getMinPNThreshold();

    /**
     * {@return a new packet number atomically allocated in this space}
     */
    long allocateNextPN();

    /**
     * This method is called by {@link QuicConnectionImpl} upon reception of
     * and successful negotiation of a new version.
     * In that case we should stop retransmitting packet that have the
     * "wrong" version: they will never be acknowledged.
     */
    void versionChanged();

    /**
     * This method is called by {@link QuicConnectionImpl} upon reception of
     * and successful processing of retry packet.
     * In that case we should treat all previously sent packets as lost.
     */
    void retry();

    /**
     * {@return a lock used by the transmission task}.
     * Used to ensure that the transmission task does not observe partial changes
     * during processing of incoming Versions and Retry packets.
     */
    ReentrantLock getTransmitLock();
    /**
     * Called when a packet is received. Causes the next ack frame to be
     * updated. If a packet contains an {@link AckFrame}, the caller is
     * expected to also later call {@link #processAckFrame(AckFrame)}
     * when processing the packet payload.
     *
     * @param packet             the received packet
     * @param packetNumber       the received packet number
     * @param isAckEliciting     whether this packet is ack eliciting
     */
    void packetReceived(PacketType packet, long packetNumber, boolean isAckEliciting);

    /**
     * Signals that a packet has been / is being retransmitted.
     * This method is called by {@link QuicConnectionImpl} when a packet has been
     * re-encrypted for retransmission.
     * <p> The retransmitted packet is taken out the pendingRetransmission list and
     * the new packet is inserted in the pendingAcknowledgement list.
     *
     * @param packet                 the new packet being retransmitted
     * @param previousPacketNumber   the packet number of the previous packet that was not acknowledged,
     *                               or -1 if this is not a retransmission
     * @param packetNumber        the new packet number under which this packet is being retransmitted
     * @throws IllegalArgumentException If {@code newPacketNumber} is lesser than 0
     */
    void packetSent(QuicPacket packet, long previousPacketNumber, long packetNumber);

    /**
     * Processes a received ACK frame.
     * This method is called by {@link QuicConnectionImpl}.
     *
     * @param frame the ACK frame received.
     */
    void processAckFrame(AckFrame frame) throws QuicTransportException;

    /**
     * Signals that the peer confirmed the handshake. Application space only.
     */
    void confirmHandshake();

    /**
     * Get the next ack frame to send.
     * This method returns the prepared ack frame if:
     * - it was not sent yet
     * - there are new ack-eliciting packets to acknowledge
     * - optionally, if the ack frame is overdue
     *
     * @param onlyOverdue if true, the frame will only be returned if it's overdue
     * @return The next AckFrame to send to the peer, or {@code null}
     * if there is nothing to acknowledge.
     */
    AckFrame getNextAckFrame(boolean onlyOverdue);

    /**
     * Get the next ack frame to send.
     * This method returns the prepared ack frame if:
     * - it was not sent yet
     * - there are new ack-eliciting packets to acknowledge
     * - the ack frame size doesn't exceed {@code maxSize}
     * - optionally, if the ack frame is overdue
     *
     * @param onlyOverdue if true, the frame will only be returned if it's overdue
     * @param maxSize
     * @return The next AckFrame to send to the peer, or {@code null}
     * if there is nothing to acknowledge.
     */
    AckFrame getNextAckFrame(boolean onlyOverdue, int maxSize);

    /**
     * Used to request sending of a ping frame, for instance, to verify that
     * the connection is alive.
     * @return a completable future that will be completed with the time it
     *   took, in milliseconds, for the peer to acknowledge the packet that
     *   contained the PingFrame (or any packet that was sent after)
     *
     * @apiNote The returned completable future is actually completed
     *  if any packet whose packet number is greater than the packet number
     *  that contained the ping frame is acknowledged.
     */
    CompletableFuture<Long> requestSendPing();

    /**
     * Stops retransmission for this packet space.
     */
    void close();

    /**
     * {@return true if this packet space is closed}
     */
    boolean isClosed();

    /**
     * Triggers immediate run of transmit loop.
     *
     * This method is called by {@link QuicConnectionImpl} when new data may be
     * available for sending, for example:
     * - new stream data is available
     * - new receive credit is available
     * - stream is forcibly closed
     */
    void runTransmitter();

    /**
     * {@return true if a packet with that packet number
     * is already being acknowledged (will be, or has been
     * acknowledged)}
     * @param packetNumber the packet number
     */
    boolean isAcknowledged(long packetNumber);

    /**
     * Immediately retransmit one unacknowledged initial packet
     * @spec https://www.rfc-editor.org/rfc/rfc9002#name-speeding-up-handshake-compl
     *       RFC 9002 6.2.3. Speeding up Handshake Completion
     */
    void fastRetransmit();
}
