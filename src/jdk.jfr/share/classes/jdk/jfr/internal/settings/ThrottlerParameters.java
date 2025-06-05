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

final class ThrottlerParameters {
    private static final long LOW_RATE_UPPER_BOUND = 9;
    private static final long WINDOW_DIVISOR = 5;
    private static final long MILLIUNITS = 1000;
    private static final long MINUTE = 60 * MILLIUNITS;
    private static final long TEN_PER_1000_MS_IN_MINUTES = 600;
    private static final long HOUR = 60 * MINUTE;
    private static final long TEN_PER_1000_MS_IN_HOURS = 36000;
    private static final long DAY = 24 * HOUR;
    private static final long TEN_PER_1000_MS_IN_DAYS = 864000;
    private static final long DEFAULT_WINDOWS_LOOKBACK_COUNT = 25; // 25 windows == 5 seconds (for default window duration of 200 ms)

    long samplePointsPerWindow;
    long windowDurationMillis;
    long windowLookBackCount;
    boolean reconfigure;

    ThrottlerParameters(long samplePointsPerWindow, long windowDuration, long windowLockBackCount) {
        this.samplePointsPerWindow = samplePointsPerWindow;
        this.windowDurationMillis = windowDuration;
        this.windowLookBackCount = windowLockBackCount;
    }

    public ThrottlerParameters copy() {
        return new ThrottlerParameters(samplePointsPerWindow, windowDurationMillis, windowLookBackCount);
    }

    void setSamplePointsAndWindowDuration(long sampleSize, long periodMillis) {
        try {
            if (sampleSize <= LOW_RATE_UPPER_BOUND) {
                samplePointsPerWindow = sampleSize;
                windowDurationMillis = periodMillis;
                return;
            }
            if (periodMillis == MINUTE && sampleSize < TEN_PER_1000_MS_IN_MINUTES) {
                samplePointsPerWindow = sampleSize;
                windowDurationMillis = periodMillis;
                return;
            }
            if (periodMillis == HOUR && sampleSize < TEN_PER_1000_MS_IN_HOURS) {
                samplePointsPerWindow = sampleSize;
                windowDurationMillis = periodMillis;
                return;
            }
            if (periodMillis == DAY && sampleSize < TEN_PER_1000_MS_IN_DAYS) {
                samplePointsPerWindow = sampleSize;
                windowDurationMillis = periodMillis;
                return;
            }
            samplePointsPerWindow = sampleSize / WINDOW_DIVISOR;
            windowDurationMillis = periodMillis / WINDOW_DIVISOR;
        } finally {
            updateWindowLookback();
        }
    }

    private void updateWindowLookback() {
        if (windowDurationMillis <= MILLIUNITS) {
            windowLookBackCount = DEFAULT_WINDOWS_LOOKBACK_COUNT; // 5 seconds
            return;
        }
        if (windowDurationMillis == MINUTE) {
            windowLookBackCount = 5; // 5 windows == 5 minutes
            return;
        }
        windowLookBackCount = 1; // 1 window == 1 hour or 1 day
    }
}
