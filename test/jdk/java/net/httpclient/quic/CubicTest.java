/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.TimeSource;
import jdk.internal.net.http.quic.*;
import jdk.internal.net.http.quic.frames.PaddingFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.packets.QuicPacket;
import org.testng.annotations.Test;

import java.time.temporal.ChronoUnit;
import java.util.List;

import static jdk.internal.net.http.quic.QuicCubicCongestionController.ALPHA;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=trace,quic:cc CubicTest
 */
public class CubicTest {
    private TimeSource timeSource = TimeSource.source();

    private class TestQuicPacket implements QuicPacket {
        private final int size;

        public TestQuicPacket(int size) {
            this.size = size;
        }

        @Override
        public List<QuicFrame> frames() {
            // fool congestion controller that this packet is in flight
            return List.of(new PaddingFrame(1));
        }

        @Override
        public QuicConnectionId destinationId() {
            throw new AssertionError("Should not come here");
        }

        @Override
        public PacketNumberSpace numberSpace() {
            throw new AssertionError("Should not come here");
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public HeadersType headersType() {
            throw new AssertionError("Should not come here");
        }

        @Override
        public PacketType packetType() {
            throw new AssertionError("Should not come here");
        }
    }

    @Test
    public void testReduction() {
        QuicRttEstimator rtt = new QuicRttEstimator();
        rtt.consumeRttSample(1, 0, Deadline.MIN);
        QuicCongestionController cc = new QuicCubicCongestionController("TEST", rtt);
        int packetSize = (int) cc.maxDatagramSize();
        assertEquals(cc.congestionWindow(), cc.initialWindow(), "Unexpected starting congestion window");
        do {
            cc.packetSent(packetSize);
            // reduce to 70% of the last value, but not below 2*SMSS
            long newCongestionWindow = Math.max((long) (QuicCubicCongestionController.BETA * cc.congestionWindow()), 2 * packetSize);
            cc.packetLost(List.of(new TestQuicPacket(packetSize)), Deadline.MAX, false);
            assertEquals(cc.congestionWindow(), newCongestionWindow, "Unexpected reduced congestion window");
        } while (cc.congestionWindow() > 2 * packetSize);
    }

    @Test
    public void testAppLimited() {
        QuicRttEstimator rtt = new QuicRttEstimator();
        rtt.consumeRttSample(1, 0, Deadline.MIN);
        QuicCongestionController cc = new QuicCubicCongestionController("TEST", rtt);
        int packetSize = (int) cc.maxDatagramSize();
        assertEquals(cc.congestionWindow(), cc.initialWindow(), "Unexpected starting congestion window");
        cc.packetSent(packetSize);
        long newCongestionWindow = (long) (QuicCubicCongestionController.BETA * cc.congestionWindow());
        // lose packet to exit slow start
        cc.packetLost(List.of(new TestQuicPacket(packetSize)), Deadline.MAX, false);
        assertEquals(cc.congestionWindow(), newCongestionWindow, "Unexpected reduced congestion window");
        Deadline sentTime = timeSource.instant().plus(1, ChronoUnit.NANOS);
        // congestion window should not increase when sender is app-limited
        cc.packetSent(packetSize);
        cc.packetAcked(packetSize, sentTime);
        assertEquals(cc.congestionWindow(), newCongestionWindow, "Unexpected congestion window change");
    }

    @Test
    public void testRenoFriendly() {
        QuicRttEstimator rtt = new QuicRttEstimator();
        rtt.consumeRttSample(1, 0, Deadline.MIN);
        QuicCongestionController cc = new QuicCubicCongestionController("TEST", rtt);
        int packetSize = (int) cc.maxDatagramSize();
        assertEquals(cc.congestionWindow(), cc.initialWindow(), "Unexpected starting congestion window");
        cc.packetSent(packetSize);
        long newCongestionWindow = (long) (QuicCubicCongestionController.BETA * cc.congestionWindow());
        // lose packet to exit slow start
        cc.packetLost(List.of(new TestQuicPacket(packetSize)), timeSource.instant(), false);
        assertEquals(cc.congestionWindow(), newCongestionWindow, "Unexpected reduced congestion window");
        // enter cwnd-limited state to start increasing cwnd
        Deadline sentTime = timeSource.instant().plus(1, ChronoUnit.NANOS);
        int numPackets = 0;
        while (!cc.isCwndLimited()) {
            cc.packetSent(packetSize);
            numPackets++;
        }
        // test that the window increases roughly by ALPHA * maxDatagramSize every RTT
        long startingCwnd = cc.congestionWindow();
        for (int i = 0; i < numPackets; i++) {
            cc.packetAcked(packetSize, sentTime);
        }
        long expectedCwnd = (long) (startingCwnd + ALPHA * packetSize);
        long actualCwnd = cc.congestionWindow();
        assertTrue(actualCwnd > expectedCwnd - numPackets && actualCwnd < expectedCwnd + numPackets,
                "actual cwnd %s not within the expected range (%s, %s)".formatted(
                        actualCwnd, expectedCwnd - numPackets, expectedCwnd + numPackets
                ));
        numPackets = 0;
        do {
            while (!cc.isCwndLimited()) {
                cc.packetSent(packetSize);
                numPackets++;
            }
            cc.packetAcked(packetSize, sentTime);
            numPackets--;
        } while (cc.congestionWindow() < cc.initialWindow());
        while (!cc.isCwndLimited()) {
            cc.packetSent(packetSize);
            numPackets++;
        }
        // test that the window increases roughly by maxDatagramSize every RTT after passing cwndPrior
        startingCwnd = cc.congestionWindow();
        for (int i = 0; i < numPackets; i++) {
            cc.packetAcked(packetSize, sentTime);
        }
        expectedCwnd = startingCwnd + packetSize;
        actualCwnd = cc.congestionWindow();
        assertTrue(actualCwnd > expectedCwnd - numPackets && actualCwnd < expectedCwnd + numPackets,
                "actual cwnd %s not within the expected range (%s, %s)".formatted(
                        actualCwnd, expectedCwnd - numPackets, expectedCwnd + numPackets
                ));
    }
}
