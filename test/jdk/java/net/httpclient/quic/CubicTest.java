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
import jdk.internal.net.http.common.TimeLine;
import jdk.internal.net.http.quic.*;
import jdk.internal.net.http.quic.frames.PaddingFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.packets.QuicPacket;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;

import static jdk.internal.net.http.quic.QuicCubicCongestionController.ALPHA;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @run junit/othervm -Djdk.httpclient.HttpClient.log=trace,quic:cc CubicTest
 */
public class CubicTest {
    static class TimeSource implements TimeLine {
        final Deadline first = jdk.internal.net.http.common.TimeSource.now();
        volatile Deadline current = first;
        public synchronized Deadline advance(long duration, TemporalUnit unit) {
            return current = current.plus(duration, unit);
        }
        public Deadline advanceMillis(long millis) {
            return advance(millis, ChronoUnit.MILLIS);
        }
        @Override
        public Deadline instant() {
            return current;
        }
    }

    private final TimeSource timeSource = new TimeSource();

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
        System.err.println("***** testReduction *****");
        QuicRttEstimator rtt = new QuicRttEstimator();
        rtt.consumeRttSample(1, 0, Deadline.MIN);
        QuicCongestionController cc = new QuicCubicCongestionController(timeSource, rtt);
        int packetSize = (int) cc.maxDatagramSize();
        assertEquals(cc.initialWindow(), cc.congestionWindow(), "Unexpected starting congestion window");
        do {
            cc.packetSent(packetSize);
            // reduce to 70% of the last value, but not below 2*SMSS
            long newCongestionWindow = Math.max((long) (QuicCubicCongestionController.BETA * cc.congestionWindow()), 2 * packetSize);
            cc.packetLost(List.of(new TestQuicPacket(packetSize)), Deadline.MAX, false);
            assertEquals(newCongestionWindow, cc.congestionWindow(), "Unexpected reduced congestion window");
        } while (cc.congestionWindow() > 2 * packetSize);
    }

    @Test
    public void testAppLimited() {
        System.err.println("***** testAppLimited *****");
        QuicRttEstimator rtt = new QuicRttEstimator();
        rtt.consumeRttSample(1, 0, Deadline.MIN);
        QuicCongestionController cc = new QuicCubicCongestionController(timeSource, rtt);
        int packetSize = (int) cc.maxDatagramSize();
        assertEquals(cc.initialWindow(), cc.congestionWindow(), "Unexpected starting congestion window");
        cc.packetSent(packetSize);
        long newCongestionWindow = (long) (QuicCubicCongestionController.BETA * cc.congestionWindow());
        // lose packet to exit slow start
        cc.packetLost(List.of(new TestQuicPacket(packetSize)), Deadline.MAX, false);
        assertEquals(newCongestionWindow, cc.congestionWindow(), "Unexpected reduced congestion window");
        Deadline sentTime = timeSource.instant().plus(1, ChronoUnit.NANOS);
        // congestion window should not increase when sender is app-limited
        cc.packetSent(packetSize);
        cc.packetAcked(packetSize, sentTime);
        assertEquals(newCongestionWindow, cc.congestionWindow(), "Unexpected congestion window change");
    }

    @Test
    public void testRenoFriendly() {
        System.err.println("***** testRenoFriendly *****");
        QuicRttEstimator rtt = new QuicRttEstimator();
        rtt.consumeRttSample(1, 0, Deadline.MIN);
        QuicCongestionController cc = new QuicCubicCongestionController(timeSource, rtt);
        int packetSize = (int) cc.maxDatagramSize();
        assertEquals(cc.initialWindow(), cc.congestionWindow(), "Unexpected starting congestion window");
        int startingWindow = (int) cc.congestionWindow();
        // lose packet to exit slow start
        cc.packetSent(packetSize);
        long newCongestionWindow = (long) (QuicCubicCongestionController.BETA * cc.congestionWindow());
        cc.packetLost(List.of(new TestQuicPacket(packetSize)), timeSource.instant(), false);
        assertEquals(newCongestionWindow, cc.congestionWindow(), "Unexpected reduced congestion window");
        // exit loss recovery to start increasing cwnd
        Deadline sentTime = timeSource.advanceMillis(1);
        do {
            // test that the window increases roughly by ALPHA * maxDatagramSize every RTT
            int startingCwnd = (int) cc.congestionWindow();
            cc.packetSent(startingCwnd);
            // we ack the entire window in one call; in practice the increase will be slower
            // because cwnd increases (and increase rate reduces) after every call to packetAcked
            cc.packetAcked(startingCwnd, sentTime);
            long expectedCwnd = (long) (startingCwnd + ALPHA * packetSize);
            long actualCwnd = cc.congestionWindow();
            assertEquals(expectedCwnd, actualCwnd,  1.0,
                    "actual cwnd not within the expected range");
        } while (cc.congestionWindow() < startingWindow);
        // test that the window increases roughly by maxDatagramSize every RTT after passing cwndPrior
        int startingCwnd = (int) cc.congestionWindow();
        cc.packetSent(startingCwnd);
        cc.packetAcked(startingCwnd, sentTime);
        int expectedCwnd = startingCwnd + packetSize;
        long actualCwnd = cc.congestionWindow();
        assertEquals(expectedCwnd, actualCwnd,  1.0,
                "actual cwnd not within the expected range");
    }

    @Test
    public void testCubic() {
        /*
         Manually created test vector:
         - ramp up the congestion window to 36 packets
         - trigger congestion; window will be reduced to 25.2 packets, K=3 seconds
         - set RTT = 1.5 seconds, advance "t" to 1.5 seconds,
           send and acknowledge a whole cwnd of data
         - cwnd should be back to 36 packets, give or take a few bytes.
         */
        System.err.println("***** testCubic *****");
        QuicRttEstimator rtt = new QuicRttEstimator();
        rtt.consumeRttSample(1_500_000, 0, Deadline.MIN);
        QuicCongestionController cc = new QuicCubicCongestionController(timeSource, rtt);
        int packetSize = (int) cc.maxDatagramSize();
        long cwnd = cc.congestionWindow();
        // ramp up the congestion window to 36 packets
        int tmp = (int) (36 * packetSize - cwnd);
        cc.packetSent(tmp + packetSize);
        cc.packetAcked(tmp, timeSource.instant());
        assertEquals(36*packetSize, cc.congestionWindow(), "Unexpected congestion window");
        long newCongestionWindow = (long) (QuicCubicCongestionController.BETA * cc.congestionWindow());
        // trigger congestion; window will be reduced to 25.2 packets, K=3 seconds
        cc.packetLost(List.of(new TestQuicPacket(packetSize)), timeSource.instant(), false);
        assertEquals(newCongestionWindow, cc.congestionWindow(), "Unexpected reduced congestion window");
        // advance "t" to 1.5 seconds,
        Deadline sentTime = timeSource.advanceMillis(1500);
        // send and acknowledge a whole cwnd of data
        tmp = (int) cc.congestionWindow();
        cc.packetSent(tmp);
        // we ack the entire window in one call; in practice the increase will be slower
        // because cwnd increases (and increase rate reduces) after every call to packetAcked
        cc.packetAcked(tmp, sentTime);
        long expectedCwnd = 36 * packetSize;
        long actualCwnd = cc.congestionWindow();
        assertEquals(expectedCwnd, actualCwnd,  1.0,
                "actual cwnd not within the expected range");
    }
}
