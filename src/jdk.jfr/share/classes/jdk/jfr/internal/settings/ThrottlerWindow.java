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
package jdk.jfr.internal.settings;

import jdk.jfr.internal.JVMSupport;

import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.internal.JVM;

final class ThrottlerWindow {
    private final AtomicLong measuredPopulationSize = new AtomicLong();
    // Guarded by Throttler.lock.
    ThrottlerParameters parameters = new ThrottlerParameters(0, 0, 0);
    long samplingInterval = 1;
    long projectedPopulationSize;

    private volatile long endTicks;

    void initialize(ThrottlerParameters parameters) {
        if (parameters.windowDurationMillis == 0) {
            endTicks = 0;
            return;
        }
        measuredPopulationSize.set(0);
        endTicks = JVM.counterTime() + JVMSupport.nanosToTicks(1_000_000L * parameters.windowDurationMillis);
    }

    boolean isExpired(long timestamp) {
        long endTicks = this.endTicks;
        if (timestamp == 0) {
            return JVM.counterTime() >= endTicks;
        } else {
            return timestamp >= endTicks;
        }
    }

    boolean sample() {
        long ordinal = measuredPopulationSize.incrementAndGet();
        return ordinal <= projectedPopulationSize && ordinal % samplingInterval == 0;
    }

    long maxSampleSize() {
        return samplingInterval == 0 ? 0 : projectedPopulationSize / samplingInterval;
    }

    long sampleSize() {
        long size = populationSize();
        return size > projectedPopulationSize ? maxSampleSize() : size / samplingInterval;
    }

    long populationSize() {
        return measuredPopulationSize.get();
    }

    long accumulatedDebt() {
        if (projectedPopulationSize == 0) {
            return 0;
        }
        return parameters.samplePointsPerWindow - maxSampleSize() + debt();
    }

    long debt() {
        if (projectedPopulationSize == 0) {
            return 0;
        }
        return sampleSize() - parameters.samplePointsPerWindow;
    }

    public String toString() {
        StringJoiner sb = new StringJoiner(", ");
        sb.add("measuredPopulationSize=" + measuredPopulationSize);
        sb.add("samplingInterval=" + samplingInterval);
        sb.add("projectedPopulationSize=" + projectedPopulationSize);
        return sb.toString();
    }
}
