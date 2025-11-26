/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.quic.packets.QuicPacket;

import java.util.Collection;

public interface QuicCongestionController {

    /**
     * {@return true if a new non-ACK packet can be sent at this time}
     */
    boolean canSendPacket();

    /**
     * Update the maximum datagram size
     * @param newSize new maximum datagram size.
     */
    void updateMaxDatagramSize(int newSize);

    /**
     * Update CC with a non-ACK packet
     * @param packetBytes packet size in bytes
     */
    void packetSent(int packetBytes);

    /**
     * Update CC after a non-ACK packet is acked
     *
     * @param packetBytes acked packet size in bytes
     * @param sentTime    time when packet was sent
     */
    void packetAcked(int packetBytes, Deadline sentTime);

    /**
     * Update CC after packets are declared lost
     *
     * @param lostPackets collection of lost packets
     * @param sentTime    time when the most recent lost packet was sent
     * @param persistent  true if persistent congestion detected, false otherwise
     */
    void packetLost(Collection<QuicPacket> lostPackets, Deadline sentTime, boolean persistent);

    /**
     * Update CC after packets are discarded
     * @param discardedPackets collection of discarded packets
     */
    void packetDiscarded(Collection<QuicPacket> discardedPackets);

    /**
     * {@return the current size of the congestion window in bytes}
     */
    long congestionWindow();

    /**
     * {@return the initial window size in bytes}
     */
    long initialWindow();

    /**
     * {@return maximum datagram size}
     */
    long maxDatagramSize();

    /**
     * {@return true if the connection is in slow start phase}
     */
    boolean isSlowStart();

    /**
     * Update the pacer with the current time
     * @param now the current time
     */
    void updatePacer(Deadline now);

    /**
     * {@return true if sending is blocked by pacer}
     */
    boolean isPacerLimited();

    /**
     * {@return true if sending is blocked by congestion window}
     */
    boolean isCwndLimited();

    /**
     * {@return deadline when pacer will unblock sending}
     */
    Deadline pacerDeadline();

    /**
     * Notify the congestion controller that sending is app-limited
     */
    void appLimited();
}
