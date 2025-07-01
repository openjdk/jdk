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
package jdk.internal.net.http.quic;

import java.util.concurrent.Executor;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.quic.frames.AckFrame;
import jdk.internal.net.http.quic.packets.PacketSpace;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicTransportException;

/**
 * This interface is a useful abstraction used to tie
 * {@link PacketSpaceManager} and {@link QuicConnectionImpl}.
 * The {@link PacketSpaceManager} uses functionalities provided
 * by a {@link PacketEmitter} when it deems that a packet needs
 * to be retransmitted, or that an acknowledgement is due.
 * It also uses the emitter's {@linkplain #timer() timer facility}
 * when it needs to register a {@link QuicTimedEvent}.
 *
 * @apiNote
 * All these methods are actually implemented by {@link QuicConnectionImpl}
 * but the {@code PacketEmitter} interface makes it possible to write
 * unit tests against a {@link PacketSpaceManager} without involving
 * any {@code QuicConnection} instance.
 *
 */
public interface PacketEmitter {
    /**
     * {@return the timer queue used by this packet emitter}
     */
    QuicTimerQueue timer();

    /**
     * Retransmit the given packet on behalf of the given packet space
     * manager.
     * @param packetSpaceManager the packet space manager on behalf of
     *                           which the packet is being retransmitted
     * @param packet the unacknowledged packet which should be retransmitted
     * @param attempts the number of previous retransmission of this packet.
     *               A value of 0 indicates the first retransmission.
     */
    void retransmit(PacketSpace packetSpaceManager, QuicPacket packet, int attempts)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Emit a possibly non ACK-eliciting packet containing the given ACK frame.
     * @param packetSpaceManager the packet space manager on behalf
     *                           of which the acknowledgement should
     *                           be sent.
     * @param ackFrame the ACK frame to be sent.
     * @param sendPing whether a PING frame should be sent.
     * @return the emitted packet number, or -1L if not applicable or not emitted
     */
    long emitAckPacket(PacketSpace packetSpaceManager, AckFrame ackFrame, boolean sendPing)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Called when a packet has been acknowledged.
     * @param packet the acknowledged packet
     */
    void acknowledged(QuicPacket packet);

    /**
     * Called when congestion controller allows sending one packet
     * @param packetNumberSpace current packet number space
     * @return true if a packet was sent, false otherwise
     */
    boolean sendData(PacketNumberSpace packetNumberSpace)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * {@return an executor to use when {@linkplain
     * jdk.internal.net.http.common.SequentialScheduler#runOrSchedule(Executor)
     * offloading loops to another thread} is required}
     */
    Executor executor();

    /**
     * Reschedule the given event on the {@link #timer() timer}
     * @param event the event to reschedule
     */
    default void reschedule(QuicTimedEvent event) {
        timer().reschedule(event);
    }

    /**
     * Reschedule the given event on the {@link #timer() timer}
     * @param event the event to reschedule
     */
    default void reschedule(QuicTimedEvent event, Deadline deadline) {
        timer().reschedule(event, deadline);
    }

    /**
     * Abort the connection if needed, for example if the peer is not responding
     * or max idle time was reached
     */
    void checkAbort(PacketNumberSpace packetNumberSpace);

    /**
     * {@return true if this emitter is open for transmitting packets, else returns false}
     */
    boolean isOpen();

    default void ptoBackoffIncreased(PacketSpaceManager space, long backoff) { };

    default String logTag() { return toString(); }
}
