/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.TimeLine;
import jdk.internal.net.http.common.TimeSource;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.frames.AckFrame;
import jdk.internal.net.http.quic.packets.QuicPacket;

import java.util.Collection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of QUIC congestion controller based on RFC 9002.
 * This is a QUIC variant of New Reno algorithm.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9002
 *      RFC 9002: QUIC Loss Detection and Congestion Control
 */
class QuicRenoCongestionController implements QuicCongestionController {
    // higher of 14720 and 2*maxDatagramSize; we use fixed maxDatagramSize
    private static final int INITIAL_WINDOW = Math.max(14720, 2 * QuicConnectionImpl.DEFAULT_DATAGRAM_SIZE);
    private static final int MAX_BYTES_IN_FLIGHT = Math.clamp(
            Utils.getLongProperty("jdk.httpclient.quic.maxBytesInFlight", 1 << 24),
            1 << 14, 1 << 24);
    private final TimeLine timeSource;
    private final String dbgTag;
    private final Lock lock = new ReentrantLock();
    private long congestionWindow = INITIAL_WINDOW;
    private int maxDatagramSize = QuicConnectionImpl.DEFAULT_DATAGRAM_SIZE;
    private int minimumWindow = 2 * maxDatagramSize;
    private long bytesInFlight;
    // maximum bytes in flight seen since the last congestion event
    private long maxBytesInFlight;
    private Deadline congestionRecoveryStartTime;
    private long ssThresh = Long.MAX_VALUE;

    public QuicRenoCongestionController(String dbgTag) {
        this.dbgTag = dbgTag;
        this.timeSource = TimeSource.source();
    }

    private boolean inCongestionRecovery(Deadline sentTime) {
        return (congestionRecoveryStartTime != null &&
                !sentTime.isAfter(congestionRecoveryStartTime));
    }

    private void onCongestionEvent(Deadline sentTime) {
        if (inCongestionRecovery(sentTime)) {
            return;
        }
        congestionRecoveryStartTime = timeSource.instant();
        ssThresh = congestionWindow / 2;
        congestionWindow = Math.max(minimumWindow, ssThresh);
        maxBytesInFlight = 0;
        if (Log.quicCC()) {
            Log.logQuic(dbgTag+ " Congestion: ssThresh: " + ssThresh +
                    ", in flight: " + bytesInFlight +
                    ", cwnd:" + congestionWindow);
        }
    }

    private static boolean inFlight(QuicPacket packet) {
        // packet is in flight if it contains anything other than a single ACK frame
        // specifically, a packet containing padding is considered to be in flight.
        return packet.frames().size() != 1 ||
                !(packet.frames().get(0) instanceof AckFrame);
    }

    @Override
    public boolean canSendPacket() {
        lock.lock();
        try {
            if (bytesInFlight >= MAX_BYTES_IN_FLIGHT) {
                return false;
            }
            var canSend = congestionWindow - bytesInFlight >= maxDatagramSize;
            return canSend;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateMaxDatagramSize(int newSize) {
        lock.lock();
        try {
            if (minimumWindow != newSize * 2) {
                minimumWindow = newSize * 2;
                maxDatagramSize = newSize;
                congestionWindow = Math.max(congestionWindow, minimumWindow);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void packetSent(int packetBytes) {
        lock.lock();
        try {
            bytesInFlight += packetBytes;
            if (bytesInFlight > maxBytesInFlight) {
                maxBytesInFlight = bytesInFlight;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void packetAcked(int packetBytes, Deadline sentTime) {
        lock.lock();
        try {
            bytesInFlight -= packetBytes;
            // RFC 9002 says we should not increase cwnd when application limited.
            // The concept itself is poorly defined.
            // Here we limit cwnd growth based on the maximum bytes in flight
            // observed since the last congestion event
            if (inCongestionRecovery(sentTime)) {
                if (Log.quicCC()) {
                    Log.logQuic(dbgTag+ " Acked, in recovery: bytes: " + packetBytes +
                            ", in flight: " + bytesInFlight);
                }
                return;
            }
            boolean isAppLimited;
            if (congestionWindow < ssThresh) {
                isAppLimited = congestionWindow >= 2 * maxBytesInFlight;
                if (!isAppLimited) {
                    congestionWindow += packetBytes;
                }
            } else {
                isAppLimited = congestionWindow > maxBytesInFlight + 2L * maxDatagramSize;
                if (!isAppLimited) {
                    congestionWindow += Math.max((long) maxDatagramSize * packetBytes / congestionWindow, 1L);
                }
            }
            if (Log.quicCC()) {
                if (isAppLimited) {
                    Log.logQuic(dbgTag+ " Acked, not blocked: bytes: " + packetBytes +
                            ", in flight: " + bytesInFlight);
                } else {
                    Log.logQuic(dbgTag + " Acked, increased: bytes: " + packetBytes +
                            ", in flight: " + bytesInFlight +
                            ", new cwnd:" + congestionWindow);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void packetLost(Collection<QuicPacket> lostPackets, Deadline sentTime, boolean persistent) {
        lock.lock();
        try {
            for (QuicPacket packet : lostPackets) {
                if (inFlight(packet)) {
                    bytesInFlight -= packet.size();
                }
            }
            onCongestionEvent(sentTime);
            if (persistent) {
                congestionWindow = minimumWindow;
                congestionRecoveryStartTime = null;
                if (Log.quicCC()) {
                    Log.logQuic(dbgTag+ " Persistent congestion: ssThresh: " + ssThresh +
                            ", in flight: " + bytesInFlight +
                            ", cwnd:" + congestionWindow);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void packetDiscarded(Collection<QuicPacket> discardedPackets) {
        lock.lock();
        try {
            for (QuicPacket packet : discardedPackets) {
                if (inFlight(packet)) {
                    bytesInFlight -= packet.size();
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
