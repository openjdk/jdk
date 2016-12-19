/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug.internal;

import static org.graalvm.compiler.debug.DebugCloseable.VOID_CLOSEABLE;

import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugTimer;
import org.graalvm.compiler.debug.TimeSource;
import org.graalvm.compiler.debug.internal.method.MethodMetricsImpl;

public final class TimerImpl extends AccumulatedDebugValue implements DebugTimer {
    private final boolean intercepting;

    /**
     * Records the most recent active timer.
     */
    private static final ThreadLocal<CloseableCounterImpl> currentTimer = new ThreadLocal<>();

    static class FlatTimer extends DebugValue implements DebugTimer {
        private TimerImpl accm;

        FlatTimer(String name, boolean conditional) {
            super(name + "_Flat", conditional);
        }

        @Override
        public String toString(long value) {
            return valueToString(value);
        }

        @Override
        public TimeUnit getTimeUnit() {
            return accm.getTimeUnit();
        }

        @Override
        public DebugCloseable start() {
            return accm.start();
        }

        @Override
        public String rawUnit() {
            return "us";
        }

        @Override
        public String toRawString(long value) {
            return valueToRawString(value);
        }
    }

    public TimerImpl(String name, boolean conditional, boolean intercepting) {
        super(name, conditional, new FlatTimer(name, conditional));
        ((FlatTimer) flat).accm = this;
        this.intercepting = intercepting;
    }

    @Override
    public DebugCloseable start() {
        if (!isConditional() || Debug.isTimeEnabled()) {
            AbstractTimer result = intercepting ? new InterceptingTimer(this) : new Timer(this);
            currentTimer.set(result);
            return result;
        } else {
            return VOID_CLOSEABLE;
        }
    }

    public static String valueToString(long value) {
        return String.format("%d.%d ms", value / 1000000, (value / 100000) % 10);
    }

    @Override
    public DebugTimer getFlat() {
        return (FlatTimer) flat;
    }

    @Override
    public String toString(long value) {
        return valueToString(value);
    }

    @Override
    public TimeUnit getTimeUnit() {
        return TimeUnit.NANOSECONDS;
    }

    private abstract class AbstractTimer extends CloseableCounterImpl implements DebugCloseable {

        private AbstractTimer(AccumulatedDebugValue counter) {
            super(currentTimer.get(), counter);
        }

        @Override
        public void close() {
            super.close();
            currentTimer.set(parent);
        }
    }

    private final class Timer extends AbstractTimer {

        private Timer(TimerImpl timer) {
            super(timer);
        }

        @Override
        protected long getCounterValue() {
            return TimeSource.getTimeNS();
        }

    }

    private final class InterceptingTimer extends AbstractTimer {

        private InterceptingTimer(TimerImpl timer) {
            super(timer);
        }

        @Override
        protected long getCounterValue() {
            return TimeSource.getTimeNS();
        }

        @Override
        protected void interceptDifferenceAccm(long difference) {
            if (Debug.isMethodMeterEnabled()) {
                MethodMetricsImpl.addToCurrentScopeMethodMetrics(counter.getName(), difference);
            }
        }

        @Override
        protected void interceptDifferenceFlat(long difference) {
            if (Debug.isMethodMeterEnabled()) {
                MethodMetricsImpl.addToCurrentScopeMethodMetrics(counter.flat.getName(), difference);
            }
        }
    }

    @Override
    public String rawUnit() {
        return "us";
    }

    @Override
    public String toRawString(long value) {
        return valueToRawString(value);
    }

    public static String valueToRawString(long value) {
        return Long.toString(value / 1000);
    }

}
