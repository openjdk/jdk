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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Utils;

/**
 * Estimator for quic connection round trip time.
 * Defined in <a href="https://www.rfc-editor.org/rfc/rfc9002#section-5">
 *     RFC 9002 section 5</a>.
 * Takes RTT samples as input (max 1 sample per ACK frame)
 * Produces:
 * - minimum RTT over a period of time (minRtt) for internal use
 * - exponentially weighted moving average (smoothedRtt)
 * - mean deviation / variation in the observed samples (rttVar)
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9002
 *      RFC 9002: QUIC Loss Detection and Congestion Control
 */
public class QuicRttEstimator {

    // The property indicates the maximum number of retries. The constant holds the value
    // 2^N where N is the value of the property (clamped between 2 and 20, default 8):
    //    1=>2 2=>4 3=>8 4=>16 5=>32 6=>64 7=>128 8=>256 ... etc
    public static final long MAX_PTO_BACKOFF = 1L << Math.clamp(
            Utils.getIntegerNetProperty("jdk.httpclient.quic.maxPtoBackoff", 8),
            2, 20);
    // The timeout calculated for PTO will stay clamped at MAX_PTO_BACKOFF_TIMEOUT if
    // the calculated value exceeds MAX_PTO_BACKOFF_TIMEOUT
    public static final Duration MAX_PTO_BACKOFF_TIMEOUT = Duration.ofSeconds(Math.clamp(
            Utils.getIntegerNetProperty("jdk.httpclient.quic.maxPtoBackoffTime", 240),
            1, 1200));
    // backoff will continue to be increased past MAX_PTO_BACKOFF if the timeout calculated
    // for PTO is less than MIN_PTO_BACKOFF_TIMEOUT
    public static final Duration MIN_PTO_BACKOFF_TIMEOUT = Duration.ofSeconds(Math.clamp(
            Utils.getIntegerNetProperty("jdk.httpclient.quic.minPtoBackoffTime", 15),
            0, 1200));

    private static final long INITIAL_RTT = TimeUnit.MILLISECONDS.toMicros(Math.clamp(
            Utils.getIntegerNetProperty("jdk.httpclient.quic.initialRTT", 333),
            50, 1000));

    // kGranularity, 1ms is recommended by RFC 9002 section 6.1.2
    private static final long GRANULARITY_MICROS = TimeUnit.MILLISECONDS.toMicros(1);
    private Deadline firstSample;
    private long latestRttMicros;
    private long minRttMicros;
    private long smoothedRttMicros = INITIAL_RTT;
    private long rttVarMicros = INITIAL_RTT / 2;
    private long ptoBackoffFactor = 1;
    private long rttSampleCount = 0;

    public record QuicRttEstimatorState(long latestRttMicros,
                                        long minRttMicros,
                                        long smoothedRttMicros,
                                        long rttVarMicros,
                                        long rttSampleCount) {}

    public synchronized QuicRttEstimatorState state() {
        return new QuicRttEstimatorState(latestRttMicros, minRttMicros, smoothedRttMicros, rttVarMicros, rttSampleCount);
    }

    /**
     * Update the estimator with latest RTT sample.
     * Use only samples where:
     * - the largest acknowledged PN is newly acknowledged
     * - at least one of the newly acked packets is ack-eliciting
     * @param latestRttMicros time between when packet was sent
     *                        and ack was received, in microseconds
     * @param ackDelayMicros ack delay received in ack frame, decoded to microseconds
     * @param now time at which latestRttMicros was calculated
     */
    public synchronized void consumeRttSample(long latestRttMicros, long ackDelayMicros, Deadline now) {
        this.rttSampleCount += 1;
        this.latestRttMicros = latestRttMicros;
        if (firstSample == null) {
            firstSample = now;
            minRttMicros = latestRttMicros;
            smoothedRttMicros = latestRttMicros;
            rttVarMicros = latestRttMicros / 2;
        } else {
            minRttMicros = Math.min(minRttMicros, latestRttMicros);
            long adjustedRtt;
            if (latestRttMicros >= minRttMicros + ackDelayMicros) {
                adjustedRtt = latestRttMicros - ackDelayMicros;
            } else {
                adjustedRtt = latestRttMicros;
            }
            rttVarMicros = (3 * rttVarMicros + Math.abs(smoothedRttMicros - adjustedRtt)) / 4;
            smoothedRttMicros = (7 * smoothedRttMicros + adjustedRtt) / 8;
        }
    }

    /**
     * {@return time threshold for time-based loss detection}
     * See <a href="https://www.rfc-editor.org/rfc/rfc9002#section-6.2.1">
     *     RFC 9002 section 6.1.2</a>
     *
     */
    public synchronized Duration getLossThreshold() {
        // max(kTimeThreshold * max(smoothed_rtt, latest_rtt), kGranularity)
        long maxRttMicros = Math.max(smoothedRttMicros, latestRttMicros);
        long lossThresholdMicros = Math.max(9*maxRttMicros / 8, GRANULARITY_MICROS);
        return Duration.of(lossThresholdMicros, ChronoUnit.MICROS);
    }

    /**
     * {@return the amount of time to wait for acknowledgement of a sent packet,
     *  excluding max ack delay}
     * See <a href="https://www.rfc-editor.org/rfc/rfc9002#section-6.2.1">
     *     RFC 9002 section 6.1.2</a>
     */
    public synchronized Duration getBasePtoDuration() {
        // PTO = smoothed_rtt + max(4*rttvar, kGranularity) + max_ack_delay
        // max_ack_delay is applied by the caller
        long basePtoMicros = smoothedRttMicros +
                Math.max(4 * rttVarMicros, GRANULARITY_MICROS);
        return Duration.of(basePtoMicros, ChronoUnit.MICROS);
    }

    public synchronized boolean isMinBackoffTimeoutExceeded() {
        return MIN_PTO_BACKOFF_TIMEOUT.compareTo(getBasePtoDuration().multipliedBy(ptoBackoffFactor)) < 0;
    }

    public synchronized long getPtoBackoff() {
        return ptoBackoffFactor;
    }

    public synchronized long increasePtoBackoff() {
        // limit to make sure we don't accidentally overflow
        if (ptoBackoffFactor <= MAX_PTO_BACKOFF || !isMinBackoffTimeoutExceeded()) {
            ptoBackoffFactor *= 2; // can go up to 2 * MAX_PTO_BACKOFF
        }
        return ptoBackoffFactor;
    }

    public synchronized void resetPtoBackoff() {
        ptoBackoffFactor = 1;
    }

}
