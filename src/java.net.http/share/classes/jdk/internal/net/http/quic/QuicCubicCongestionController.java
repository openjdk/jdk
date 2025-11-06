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
import jdk.internal.net.http.common.Log;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of the CUBIC congestion controller
 * based on RFC 9438.
 *
 * @spec https://www.rfc-editor.org/rfc/rfc9438.html
 *       RFC 9438: CUBIC for Fast and Long-Distance Networks
 */
class QuicCubicCongestionController extends QuicBaseCongestionController {

    private static final double BETA = 0.7;
    private static final double ALPHA = 3 * (1 - BETA) / (1 + BETA);
    private static final double C = 0.4;
    private final QuicRttEstimator rttEstimator;
    // Cubic curve inflection point, in bytes
    private long wMaxBytes;
    // cwnd before the most recent congestion event
    private long cwndPriorBytes;
    // "t" from RFC 9438
    private long timeNanos;
    // "K" from RFC 9438
    private long kNanos;
    // estimate for the Reno-friendly congestion window
    private long wEstBytes;
    // the most recent time when the congestion window was filled
    private Deadline lastFullWindow;

    public QuicCubicCongestionController(String dbgTag, QuicRttEstimator rttEstimator) {
        super(dbgTag, rttEstimator);
        this.rttEstimator = rttEstimator;
    }

    @Override
    public void packetSent(int packetBytes) {
        lock.lock();
        try {
            super.packetSent(packetBytes);
            if (isCwndLimited()) {
                Deadline now = timeSource.instant();
                if (lastFullWindow == null) {
                    lastFullWindow = now;
                } else {
                    long timePassedNanos = Deadline.between(lastFullWindow, now).toNanos();
                    if (timePassedNanos > 0) {
                        long rttNanos = TimeUnit.MICROSECONDS.toNanos(rttEstimator.state().smoothedRttMicros());
                        timeNanos += Math.min(timePassedNanos, rttNanos);
                        lastFullWindow = now;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }


    protected boolean congestionAvoidanceAcked(int packetBytes, Deadline sentTime) {
        boolean isAppLimited;
        isAppLimited = sentTime.isAfter(lastFullWindow);
        if (!isAppLimited) {
            // C * (t-K [seconds])^3 + Wmax (segments)
            if (wEstBytes < cwndPriorBytes) {
                wEstBytes += Math.max((long) (ALPHA * packetBytes / congestionWindow), 1);
            } else {
                wEstBytes += Math.max(packetBytes / congestionWindow, 1);
            }
            long targetBytes = (long)(C * maxDatagramSize * Math.pow((timeNanos - kNanos) / 1e9, 3)) + wMaxBytes;
            if (targetBytes > 1.5 * congestionWindow) {
                targetBytes = (long) (1.5 * congestionWindow);
            }
            if (targetBytes > congestionWindow) {
                congestionWindow += Math.max((targetBytes - congestionWindow) * packetBytes / congestionWindow, 1L);
            }
            if (wEstBytes > congestionWindow) {
                congestionWindow = wEstBytes;
            }
        }
        return isAppLimited;
    }

    protected void onCongestionEvent(Deadline sentTime) {
        if (inCongestionRecovery(sentTime)) {
            return;
        }
        // TODO implement fast convergence (RFC 9438 section 4.7)
        wMaxBytes = congestionWindow;
        cwndPriorBytes = congestionWindow;
        congestionRecoveryStartTime = timeSource.instant();
        ssThresh = (long)(congestionWindow * BETA);
        wEstBytes = congestionWindow = Math.max(minimumWindow, ssThresh);
        maxBytesInFlight = 0;
        timeNanos = 0;
        // set lastFullWindow to prevent rapid timeNanos growth
        lastFullWindow = congestionRecoveryStartTime;
        // ((wmax_segments - cwnd_segments) / C) ^ (1/3) seconds
        kNanos = (long)(Math.cbrt((wMaxBytes - congestionWindow) / C / maxDatagramSize) * 1_000_000_000);
        if (Log.quicCC()) {
            Log.logQuic(dbgTag + " Congestion: ssThresh: " + ssThresh +
                    ", in flight: " + bytesInFlight +
                    ", cwnd:" + congestionWindow +
                    ", k: " + kNanos + " ns");
        }
    }
}
