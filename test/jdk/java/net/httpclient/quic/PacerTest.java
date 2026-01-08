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
import jdk.internal.net.http.quic.QuicCongestionController;
import jdk.internal.net.http.quic.QuicPacer;
import jdk.internal.net.http.quic.QuicRttEstimator;
import jdk.internal.net.http.quic.packets.QuicPacket;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @run testng/othervm -Djdk.httpclient.quic.timerFrequency=1000 PacerTest
 */
public class PacerTest {

    private static class TestCongestionController implements QuicCongestionController {
        private long cwnd;
        private long iw;
        private long maxDatagramSize;
        private boolean isSlowStart;

        private TestCongestionController(long cwnd, long iw, long maxDatagramSize, boolean isSlowStart) {
            this.cwnd = cwnd;
            this.iw = iw;
            this.maxDatagramSize = maxDatagramSize;
            this.isSlowStart = isSlowStart;
        }

        @Override
        public boolean canSendPacket() {
            throw new AssertionError("Should not come here");
        }

        @Override
        public void updateMaxDatagramSize(int newSize) {
            throw new AssertionError("Should not come here");
        }

        @Override
        public void packetSent(int packetBytes) {
            throw new AssertionError("Should not come here");
        }

        @Override
        public void packetAcked(int packetBytes, Deadline sentTime) {
            throw new AssertionError("Should not come here");
        }

        @Override
        public void packetLost(Collection<QuicPacket> lostPackets, Deadline sentTime, boolean persistent) {
            throw new AssertionError("Should not come here");
        }

        @Override
        public void packetDiscarded(Collection<QuicPacket> discardedPackets) {
            throw new AssertionError("Should not come here");
        }

        @Override
        public long congestionWindow() {
            return cwnd;
        }

        @Override
        public long initialWindow() {
            return iw;
        }

        @Override
        public long maxDatagramSize() {
            return maxDatagramSize;
        }

        @Override
        public boolean isSlowStart() {
            return isSlowStart;
        }

        @Override
        public void updatePacer(Deadline now) {
            throw new AssertionError("Should not come here");
        }

        @Override
        public boolean isPacerLimited() {
            throw new AssertionError("Should not come here");
        }

        @Override
        public boolean isCwndLimited() {
            throw new AssertionError("Should not come here");
        }

        @Override
        public Deadline pacerDeadline() {
            throw new AssertionError("Should not come here");
        }

        @Override
        public void appLimited() {
            throw new AssertionError("Should not come here");
        }
    }

    public record TestCase(int maxDatagramSize, int packetsInIW, int packetsInCwnd, int millisInRtt,
                           int initialPermit, int periodicPermit, boolean slowStart) {
    }

    @DataProvider
    public Object[][] pacerFirstFlight() {
        return List.of(
                        // Should permit initial window before blocking
                        new TestCase(1200, 10, 32, 16, 10, 4, true),
                        // Should permit 2*cwnd/rtt packets before blocking
                        new TestCase(1200, 10, 128, 16, 16, 16, true),
                        // Should permit 1.25*cwnd/rtt packets before blocking
                        new TestCase(1200, 10, 256, 16, 20, 20, false)
                ).stream().map(Stream::of)
                .map(Stream::toArray)
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "pacerFirstFlight")
    public void testBasicPacing(TestCase test) {
        int maxDatagramSize = test.maxDatagramSize;
        int packetsInIW = test.packetsInIW;
        int packetsInCwnd = test.packetsInCwnd;
        int millisInRtt = test.millisInRtt;
        int permit = test.initialPermit;
        QuicCongestionController cc = new TestCongestionController(packetsInCwnd * maxDatagramSize,
                maxDatagramSize * packetsInIW, maxDatagramSize, test.slowStart);
        QuicRttEstimator rtt = new QuicRttEstimator();
        rtt.consumeRttSample(1000 * millisInRtt, 0, Deadline.MIN);
        QuicPacer pacer = new QuicPacer(rtt, cc);
        pacer.updateQuota(Deadline.MIN);
        for (int i = 0; i < permit; i++) {
            assertTrue(pacer.canSend(), "Pacer blocked after " + i + " packets");
            pacer.packetSent(maxDatagramSize);
        }
        assertFalse(pacer.canSend(), "Pacer didn't block");
        Deadline next = pacer.twoPacketDeadline();
        pacer.updateQuota(next);
        for (int i = 0; i < 2; i++) {
            assertTrue(pacer.canSend(), "Two packet deadline: pacer blocked after " + i + " packets");
            pacer.packetSent(maxDatagramSize);
        }
        assertFalse(pacer.canSend(), "Two packet deadline: pacer didn't block");
        next = next.plus(1, ChronoUnit.MILLIS);
        pacer.updateQuota(next);
        for (int i = 0; i < test.periodicPermit; i++) {
            assertTrue(pacer.canSend(), "One millisecond: pacer blocked after " + i + " packets");
            pacer.packetSent(maxDatagramSize);
        }
        assertFalse(pacer.canSend(), "One millisecond: pacer didn't block");
        next = next.plus(3, ChronoUnit.MILLIS);
        pacer.updateQuota(next);
        // Quota capped at two millisecond equivalent
        for (int i = 0; i < 2 * test.periodicPermit; i++) {
            assertTrue(pacer.canSend(), "Three milliseconds: pacer blocked after " + i + " packets");
            pacer.packetSent(maxDatagramSize);
        }
        assertFalse(pacer.canSend(), "Three milliseconds: pacer didn't block");
        next = next.plus(3, ChronoUnit.MILLIS);
        pacer.appLimited();
        pacer.updateQuota(next);
        // App-limited: quota capped at initialPermit
        for (int i = 0; i < test.initialPermit; i++) {
            assertTrue(pacer.canSend(), "App limited: pacer blocked after " + i + " packets");
            pacer.packetSent(maxDatagramSize);
        }
        assertFalse(pacer.canSend(), "App limited: pacer didn't block");
    }

    @Test
    public void testPacingShortRtt() {
        int maxDatagramSize = 1200;
        int packetsInIW = 10;
        int packetsInCwnd = 32;
        QuicCongestionController cc = new TestCongestionController(packetsInCwnd * maxDatagramSize,
                maxDatagramSize * packetsInIW, maxDatagramSize, true);
        QuicRttEstimator rtt = new QuicRttEstimator();
        rtt.consumeRttSample(1000, 0, Deadline.MIN);
        QuicPacer pacer = new QuicPacer(rtt, cc);
        pacer.updateQuota(Deadline.MIN);
        for (int i = 0; i < 2 * packetsInCwnd; i++) {
            assertTrue(pacer.canSend(), "Pacer blocked after " + i + " packets");
            pacer.packetSent(maxDatagramSize);
        }
        assertFalse(pacer.canSend(), "Pacer didn't block");
        // when RTT is short, permit cwnd on every update
        Deadline next = pacer.twoPacketDeadline();
        pacer.updateQuota(next);
        for (int i = 0; i < 2 * packetsInCwnd; i++) {
            assertTrue(pacer.canSend(), "Two packet deadline: pacer blocked after " + i + " packets");
            pacer.packetSent(maxDatagramSize);
        }
        assertFalse(pacer.canSend(), "Two packet deadline: pacer didn't block");
    }

    @Test
    public void testPacingSmallCwnd() {
        int maxDatagramSize = 1200;
        int packetsInIW = 10;
        int packetsInCwnd = 2;
        int millisInRtt = 16;
        QuicCongestionController cc = new TestCongestionController(packetsInCwnd * maxDatagramSize,
                maxDatagramSize * packetsInIW, maxDatagramSize, true);
        QuicRttEstimator rtt = new QuicRttEstimator();
        rtt.consumeRttSample(1000 * millisInRtt, 0, Deadline.MIN);
        QuicPacer pacer = new QuicPacer(rtt, cc);
        // first quota update is capped to IW
        pacer.updateQuota(Deadline.MIN);
        // update quota again. This time it's capped to 4 packets
        pacer.updateQuota(Deadline.MIN.plusNanos(1));
        for (int i = 0; i < 4; i++) {
            assertTrue(pacer.canSend(), "Pacer blocked after " + i + " packets");
            pacer.packetSent(maxDatagramSize);
        }
        assertFalse(pacer.canSend(), "Pacer didn't block");
        Deadline next = pacer.twoPacketDeadline();
        pacer.updateQuota(next);
        for (int i = 0; i < 2; i++) {
            assertTrue(pacer.canSend(), "Two packet deadline: pacer blocked after " + i + " packets");
            pacer.packetSent(maxDatagramSize);
        }
        assertFalse(pacer.canSend(), "Two packet deadline: pacer didn't block");
        // pacing rate is 1 packet per 4 milliseconds
        next = next.plus(4, ChronoUnit.MILLIS);
        pacer.updateQuota(next);
        assertTrue(pacer.canSend(), "Pacer blocked after 4 millis");
        pacer.packetSent(maxDatagramSize);
        assertFalse(pacer.canSend(), "Pacer permitted 2 packets after 4 millis");

        next = next.plus(2, ChronoUnit.MILLIS);
        pacer.updateQuota(next);
        assertFalse(pacer.canSend(), "Pacer permitted a packet after 2 millis");
        next = next.plus(2, ChronoUnit.MILLIS);
        pacer.updateQuota(next);
        assertTrue(pacer.canSend(), "Pacer blocked after 2x2 millis");
        pacer.packetSent(maxDatagramSize);
        assertFalse(pacer.canSend(), "Pacer permitted 2 packets after 2x2 millis");
    }
}
