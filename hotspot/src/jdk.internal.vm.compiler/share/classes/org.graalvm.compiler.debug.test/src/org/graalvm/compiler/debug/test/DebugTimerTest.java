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

import static org.junit.Assert.assertEquals;

import java.lang.management.ThreadMXBean;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugConfig;
import org.graalvm.compiler.debug.DebugConfigScope;
import org.graalvm.compiler.debug.DebugTimer;
import org.graalvm.compiler.debug.Management;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.util.EconomicMap;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("try")
public class DebugTimerTest {

    private static final ThreadMXBean threadMXBean = Management.getThreadMXBean();

    @Before
    public void checkCapabilities() {
        Assume.assumeTrue("skipping management interface test", threadMXBean.isCurrentThreadCpuTimeSupported());
    }

    /**
     * Actively spins the current thread for at least a given number of milliseconds in such a way
     * that timers for the current thread keep ticking over.
     *
     * @return the number of milliseconds actually spent spinning which is guaranteed to be >=
     *         {@code ms}
     */
    private static long spin(long ms) {
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
        OptionValues options = new OptionValues(EconomicMap.create());
        DebugConfig debugConfig = Debug.fixedConfig(options, 0, 0, false, false, true, false, false, null, null, System.out);
        try (DebugConfigScope dcs = new DebugConfigScope(debugConfig); Debug.Scope s = Debug.scope("DebugTimerTest")) {
            DebugTimer timerC = Debug.timer("TimerC");
            try (DebugCloseable c1 = timerC.start()) {
                spin(50);
                try (DebugCloseable c2 = timerC.start()) {
                    spin(50);
                    try (DebugCloseable c3 = timerC.start()) {
                        spin(50);
                        try (DebugCloseable c4 = timerC.start()) {
                            spin(50);
                            try (DebugCloseable c5 = timerC.start()) {
                                spin(50);
                            }
                        }
                    }
                }
            }
            if (timerC.getFlat() != null) {
                assertEquals(timerC.getFlat().getCurrentValue(), timerC.getCurrentValue());
            }
        }
    }
}
