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

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import jdk.jfr.internal.PlatformEventType;
public final class Throttler {
    private static final ThrottlerParameters DISABLED_PARAMETERS = new ThrottlerParameters(0, 0, 0);
    private static final long MILLIUNITS = 1000;
    private static final long MINUTE = 60 * MILLIUNITS;
    private static final long TEN_PER_1000_MS_IN_MINUTES = 600;
    private static final long HOUR = 60 * MINUTE;
    private static final long TEN_PER_1000_MS_IN_HOURS = 36000;
    private static final long TEN_PER_1000_MS_IN_DAYS = 864000;
    private static final long EVENT_THROTTLER_OFF = -2;

    private final ReentrantLock lock = new ReentrantLock();
    private final Random randomGenerator = new Random();
    private final ThrottlerWindow window0 = new ThrottlerWindow();
    private final ThrottlerWindow window1 = new ThrottlerWindow();

    private volatile ThrottlerWindow activeWindow = window0;

    // Guarded by lock
    private double averagePopulationSize;
    private double ewmaPopulationSize;
    private long accumulatedDebtCarryLimit;
    private long accumulatedDebtCarryCount;
    private ThrottlerParameters lastParameters = new ThrottlerParameters(0, 0, 0);
    private long sampleSize;
    private long periodMillis;
    private boolean disabled;
    private boolean update = true;

    public Throttler(PlatformEventType t) {
    }
    // Not synchronized in fast path, but uses volatile reads.
    public boolean sample(long ticks) {
        if (disabled) {
            return true;
        }
        ThrottlerWindow current = activeWindow;
        if (current.isExpired(ticks)) {
            if (lock.tryLock()) {
                try {
                    rotateWindow(ticks);
                } finally {
                    lock.unlock();
                }
            }
            return activeWindow.sample();
        }
        return current.sample();
    }

    public void configure(long sampleSize, long periodMillis) {
        lock.lock();
        try {
            this.sampleSize = sampleSize;
            this.periodMillis = periodMillis;
            this.update = true;
            this.activeWindow = configure(nextWindowParameters(), activeWindow);
        } finally {
            lock.unlock();
        }
    }

    private ThrottlerWindow configure(ThrottlerParameters parameters, ThrottlerWindow expired) {
        if (parameters.reconfigure) {
            // Store updated params once to both windows
            expired.parameters = parameters;
            nextWindow(expired).parameters = parameters;
            configure(parameters);
        }
        ThrottlerWindow next = setRate(parameters, expired);
        next.initialize(parameters);
        return next;
    }

    private void configure(ThrottlerParameters parameters) {
        averagePopulationSize = 0;
        ewmaPopulationSize = computeEwmaAlphaCoefficient(parameters.windowLookBackCount);
        accumulatedDebtCarryLimit = computeAccumulatedDebtCarryLimit(parameters);
        accumulatedDebtCarryCount = accumulatedDebtCarryLimit;
        parameters.reconfigure = false;
    }

    private void rotateWindow(long ticks) {
        ThrottlerWindow current = activeWindow;
        if (current.isExpired(ticks)) {
            activeWindow = configure(current.parameters.copy(), current);
        }
    }

    private ThrottlerWindow setRate(ThrottlerParameters parameters, ThrottlerWindow expired) {
        ThrottlerWindow next = nextWindow(expired);
        long projectedSampleSize = parameters.samplePointsPerWindow + amortizeDebt(expired);
        if (projectedSampleSize == 0) {
            next.projectedPopulationSize = 0;
            return next;
        }
        next.samplingInterval = deriveSamplingInterval(projectedSampleSize, expired);
        next.projectedPopulationSize = projectedSampleSize * next.samplingInterval;
        return next;
    }

    private long amortizeDebt(ThrottlerWindow expired) {
        long accumulatedDebt = expired.accumulatedDebt();
        if (accumulatedDebtCarryCount == accumulatedDebtCarryLimit) {
            accumulatedDebtCarryCount = 1;
            return 0;
        }
        accumulatedDebtCarryCount++;
        return -accumulatedDebt;
    }

    private long deriveSamplingInterval(double sampleSize, ThrottlerWindow expired) {
        double populationSize = projectPopulationSize(expired);
        if (populationSize <= sampleSize) {
            return 1;
        }
        double projectProbability = sampleSize / populationSize;
        return nextGeometric(projectProbability, randomGenerator.nextDouble());
    }

    private double projectPopulationSize(ThrottlerWindow expired) {
        averagePopulationSize = exponentiallyWeightedMovingAverage(expired.populationSize(), ewmaPopulationSize, averagePopulationSize);
        return averagePopulationSize;
    }

    private static long nextGeometric(double p, double u) {
        return (long) Math.ceil(Math.log(1.0 - adjustBoundary(u)) / Math.log(1.0 - p));
    }

    private static double adjustBoundary(double u) {
        if (u == 0.0) {
            return 0.01;
        }
        if (u == 1.0) {
            return 0.99;
        }
        return u;
    }

    private void normalize() {
        if (periodMillis == MILLIUNITS) {
            return;
        }
        if (periodMillis == MINUTE) {
            if (sampleSize >= TEN_PER_1000_MS_IN_MINUTES) {
                sampleSize /= 60;
                periodMillis /= 60;
            }
            return;
        }
        if (periodMillis == HOUR) {
            if (sampleSize >= TEN_PER_1000_MS_IN_HOURS) {
                sampleSize /= 3600;
                periodMillis /= 3600;
            }
            return;
        }
        if (sampleSize >= TEN_PER_1000_MS_IN_DAYS) {
            sampleSize /= 86400;
            periodMillis /= 86400;
        }
    }

    private ThrottlerParameters nextWindowParameters() {
        if (update) {
            return updateParameters();
        }
        return disabled ? DISABLED_PARAMETERS : lastParameters;
    }

    private ThrottlerParameters updateParameters() {
        disabled = is_disabled(sampleSize);
        if (disabled) {
            return DISABLED_PARAMETERS;
        }
        normalize();
        lastParameters.setSamplePointsAndWindowDuration(sampleSize, periodMillis);
        lastParameters.reconfigure = true;
        update = false;
        return lastParameters;
    }

    private boolean is_disabled(long eventSampleSize) {
        return eventSampleSize == EVENT_THROTTLER_OFF;
    }

    private double exponentiallyWeightedMovingAverage(double y, double alpha, double s) {
        return alpha * y + (1 - alpha) * s;
    }

    private double computeEwmaAlphaCoefficient(long lookBackCount) {
        return lookBackCount <= 1 ? 1.0 : 1.0 / lookBackCount;
    }

    private long computeAccumulatedDebtCarryLimit(ThrottlerParameters parameters) {
        if (parameters.windowDurationMillis == 0 || parameters.windowDurationMillis >= 1000) {
            return 1;
        }
        return 1000 / parameters.windowDurationMillis;
    }

    private ThrottlerWindow nextWindow(ThrottlerWindow expired) {
        return expired == window0 ? window1 : window0;
    }
}
