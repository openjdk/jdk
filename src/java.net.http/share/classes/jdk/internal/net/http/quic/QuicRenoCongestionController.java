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

/**
 * Implementation of QUIC congestion controller based on RFC 9002.
 * This is a QUIC variant of New Reno algorithm.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9002
 *      RFC 9002: QUIC Loss Detection and Congestion Control
 */
final class QuicRenoCongestionController extends QuicBaseCongestionController {
    public QuicRenoCongestionController(String dbgTag, QuicRttEstimator rttEstimator) {
        super(dbgTag, rttEstimator);
    }

    boolean congestionAvoidanceAcked(int packetBytes, Deadline sentTime) {
        boolean isAppLimited = congestionWindow > maxBytesInFlight + 2L * maxDatagramSize;
        if (!isAppLimited) {
            congestionWindow += Math.max((long) maxDatagramSize * packetBytes / congestionWindow, 1L);
        }
        return isAppLimited;
    }

    void onCongestionEvent(Deadline sentTime) {
        if (inCongestionRecovery(sentTime)) {
            return;
        }
        congestionRecoveryStartTime = timeSource.instant();
        ssThresh = congestionWindow / 2;
        congestionWindow = Math.max(minimumWindow, ssThresh);
        maxBytesInFlight = 0;
        if (Log.quicCC()) {
            Log.logQuic(dbgTag + " Congestion: ssThresh: " + ssThresh +
                    ", in flight: " + bytesInFlight +
                    ", cwnd:" + congestionWindow);
        }
    }
}
