/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug.test;

import static org.graalvm.compiler.debug.DebugContext.DEFAULT_LOG_STREAM;
import static org.graalvm.compiler.debug.DebugContext.NO_CONFIG_CUSTOMIZERS;
import static org.graalvm.compiler.debug.DebugContext.NO_DESCRIPTION;
import static org.graalvm.compiler.debug.DebugContext.NO_GLOBAL_METRIC_VALUES;
import static org.junit.Assert.assertEquals;

import java.lang.management.ThreadMXBean;

import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.Management;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.util.EconomicMap;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("try")
public class TimerKeyTest {

    @Before
    public void checkCapabilities() {
        try {
            ThreadMXBean threadMXBean = Management.getThreadMXBean();
            Assume.assumeTrue("skipping management interface test", threadMXBean.isCurrentThreadCpuTimeSupported());
        } catch (LinkageError err) {
            Assume.assumeNoException("Cannot run without java.management JDK9 module", err);
        }
    }

    /**
     * Actively spins the current thread for at least a given number of milliseconds in such a way
     * that timers for the current thread keep ticking over.
     *
     * @return the number of milliseconds actually spent spinning which is guaranteed to be >=
     *         {@code ms}
     */
    private static long spin(long ms) {
        ThreadMXBean threadMXBean = Management.getThreadMXBean();
        long start = threadMXBean.getCurrentThreadCpuTime();
        do {
            long durationMS = (threadMXBean.getCurrentThreadCpuTime() - start) / 1000;
            if (durationMS >= ms) {
                return durationMS;
            }
        } while (true);
    }

    /**
     * Asserts that a timer replied recursively without any other interleaving timers has the same
     * flat and accumulated times.
     */
    @Test
    public void test2() {
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        map.put(DebugOptions.Time, "");
        OptionValues options = new OptionValues(map);
        DebugContext debug = DebugContext.create(options, NO_DESCRIPTION, NO_GLOBAL_METRIC_VALUES, DEFAULT_LOG_STREAM, NO_CONFIG_CUSTOMIZERS);

        TimerKey timerC = DebugContext.timer("TimerC");
        try (DebugCloseable c1 = timerC.start(debug)) {
            spin(50);
            try (DebugCloseable c2 = timerC.start(debug)) {
                spin(50);
                try (DebugCloseable c3 = timerC.start(debug)) {
                    spin(50);
                    try (DebugCloseable c4 = timerC.start(debug)) {
                        spin(50);
                        try (DebugCloseable c5 = timerC.start(debug)) {
                            spin(50);
                        }
                    }
                }
            }
        }
        if (timerC.getFlat() != null) {
            assertEquals(timerC.getFlat().getCurrentValue(debug), timerC.getCurrentValue(debug));
        }
    }
}
