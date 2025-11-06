/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.net.http.common.Utils;
import jdk.internal.util.OperatingSystem;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of pacing.
 *
 * When the connection is sending at a rate lower than permitted
 * by the congestion controller, pacer is responsible for spreading out
 * the outgoing packets across the entire RTT.
 *
 * Technically the pacer provides two functions:
 * - computes the number of packets that can be sent now
 * - computes the time when another packet can be sent
 *
 * When a new flow starts, or when the flow is not pacer-limited,
 * the pacer limits the window to:
 * max(INITIAL_WINDOW, pacingRate / timerFreq)
 * timerFreq is the best timer resolution we can get from the selector.
 * pacingRate is N * congestionWindow / smoothedRTT
 * where N = 2 when in slow start, N = 1.25 otherwise.
 *
 * After that, the window refills at pacingRate, up to two timer periods or 4 packets,
 * whichever is higher.
 *
 * The time when another packet can be sent is computed
 * as the time when the window will allow at least 2 packets.
 *
 * All methods are externally synchronized in congestion controller.
 *
 * Ideas taken from:
 * https://www.rfc-editor.org/rfc/rfc9002.html#name-pacing
 * https://www.ietf.org/archive/id/draft-welzl-iccrg-pacing-03.html
 */
public class QuicPacer {

    // usually 64 Hz on Windows, 1000 on Linux
    private static final long DEFAULT_TIMER_FREQ_HZ = OperatingSystem.isWindows() ? 64 : 1000;
    private static final long TIMER_FREQ_HZ = Math.clamp(
            Utils.getLongProperty("jdk.httpclient.quic.timerFrequency", DEFAULT_TIMER_FREQ_HZ),
            1, 1000);

    private final QuicRttEstimator rttEstimator;
    private final QuicCongestionController congestionController;

    private boolean appLimited;
    private long quota;
    private Deadline lastUpdate;

    /**
     * Create a QUIC pacer for the given RTT estimator and congestion controller
     *
     * @param rttEstimator         the RTT estimator
     * @param congestionController the congestion controller
     */
    public QuicPacer(QuicRttEstimator rttEstimator,
                     QuicCongestionController congestionController) {
        this.rttEstimator = rttEstimator;
        this.congestionController = congestionController;
        this.appLimited = true;
    }

    /**
     * called to indicate that the flow is app-limited.
     * Alters the behavior of the following updateQuota call.
     */
    public void appLimited() {
        appLimited = true;
    }

    /**
     * {@return true if pacer quota not hit yet, false otherwise}
     */
    public boolean canSend() {
        return quota >= congestionController.maxDatagramSize();
    }

    /**
     * Update quota based on time since the last call to this method
     * and whether appLimited() was called or not.
     *
     * @param now current time
     */
    public void updateQuota(Deadline now) {
        if (lastUpdate != null && !now.isAfter(lastUpdate)) {
            // might happen when transmission tasks from different packet spaces
            // race to update quota. Keep the most recent update only.
            return;
        }
        long rttMicros = rttEstimator.state().smoothedRttMicros();
        long cwnd = congestionController.congestionWindow();
        if (rttMicros * TIMER_FREQ_HZ < TimeUnit.SECONDS.toMicros(2)) {
            // RTT less than two timer periods; don't pace
            quota = 2 * cwnd;
            lastUpdate = now;
            return;
        }
        long pacingRate = cwnd * (congestionController.isSlowStart() ? 2_000_000 : 1_250_000) / rttMicros; // bytes per second
        long initialWindow = congestionController.initialWindow();
        long onePeriodWindow = pacingRate / TIMER_FREQ_HZ;
        long maxQuota;
        if (appLimited) {
            maxQuota = Math.max(initialWindow, onePeriodWindow);
        } else {
            maxQuota = Math.max(2 * onePeriodWindow, 4 * congestionController.maxDatagramSize());
        }
        if (lastUpdate == null) {
            quota = Math.max(initialWindow, maxQuota);
        } else {
            long nanosSinceUpdate = Deadline.between(lastUpdate, now).toNanos();
            if (nanosSinceUpdate >= TimeUnit.MICROSECONDS.toNanos(rttMicros)) {
                // don't bother computing the increment, it might overflow and will be capped to maxQuota anyway
                quota = maxQuota;
                if (Log.quicCC()) {
                    Log.logQuic("pacer cwnd: %s, rtt %s us, duration %s ns, quota: %s".formatted(
                            cwnd, rttMicros, nanosSinceUpdate, quota));
                }
            } else {
                long quotaIncrement = pacingRate * nanosSinceUpdate / 1_000_000_000;
                quota += quotaIncrement;
                quota = Math.min(quota, maxQuota);
                if (Log.quicCC()) {
                    Log.logQuic("pacer cwnd: %s, rtt %s us, duration %s ns, increment %s, quota %s".formatted(
                            cwnd, rttMicros, nanosSinceUpdate, quotaIncrement, quota));
                }
            }
        }
        lastUpdate = now;
        appLimited = false;
    }

    /**
     * {@return the deadline when quota will increase to two packets}
     */
    public Deadline twoPacketDeadline() {
        long datagramSize = congestionController.maxDatagramSize();
        long quotaNeeded = datagramSize * 2 - quota;
        if (quotaNeeded <= 0) {
            assert canSend();
            return lastUpdate;
        }
        // Window increases at a rate of rtt / cwnd / N
        long rttMicros = rttEstimator.state().smoothedRttMicros();
        long cwnd = congestionController.congestionWindow();
        return lastUpdate.plus(rttMicros
                * (congestionController.isSlowStart() ? 500 : 800) /* 1000/N */
                * quotaNeeded / cwnd, ChronoUnit.NANOS);
    }

    /**
     * called to indicate that a packet was sent
     *
     * @param packetBytes packet size in bytes
     */
    public void packetSent(int packetBytes) {
        quota -= packetBytes;
    }
}
