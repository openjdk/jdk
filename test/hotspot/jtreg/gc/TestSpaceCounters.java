/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package gc;

/**
 * @test id=Serial
 * @requires vm.gc.Serial
 * @summary Verify the expected space counters exist.
 * @modules java.base/jdk.internal.misc
 * @modules java.management/sun.management
 * @modules jdk.internal.jvmstat/sun.jvmstat.monitor
 * @library /test/lib /
 * @run main/othervm -XX:+UseSerialGC -XX:+UsePerfData gc.TestSpaceCounters
 */

/**
 * @test id=Parallel
 * @requires vm.gc.Parallel
 * @summary Verify the expected space counters exist.
 * @modules java.base/jdk.internal.misc
 * @modules java.management/sun.management
 * @modules jdk.internal.jvmstat/sun.jvmstat.monitor
 * @library /test/lib /
 * @run main/othervm -XX:+UseParallelGC -XX:+UsePerfData gc.TestSpaceCounters
 */

import gc.testlibrary.Helpers;
import gc.testlibrary.PerfCounter;
import gc.testlibrary.PerfCounters;
import sun.jvmstat.monitor.MonitorException;

public class TestSpaceCounters {
    private static final String GENERATION_NAMESPACE = "sun.gc.generation.";

    // Each space has these counters.
    private static final String[] COUNTER_NAMES = {
        "maxCapacity", "capacity", "used", "initCapacity" };

    private static String counterName(String name, int generation, int space) {
        return GENERATION_NAMESPACE + generation + ".space." + space + "." + name;
    }

    private static PerfCounter counter(String name, int generation, int space) {
        String cname = counterName(name, generation, space);
        try {
            return PerfCounters.findByName(cname);
        } catch (MonitorException e) {
            throw new RuntimeException(e.toString());
        }
    }

    private static long value(String name, int generation, int space) {
        PerfCounter pc = counter(name, generation, space);
        return pc.longValue();
    }

    private static void checkCounters(int generation, int space) {
        for (int i = 0; i < COUNTER_NAMES.length; ++i) {
            value(COUNTER_NAMES[i], generation, space);
        }
    }

    private static final int YOUNG_GENERATION = 0;
    private static final int OLD_GENERATION = 1;

    public static void main(String[] args) {
        // Young Generation has 3 spaces - eden, and two survivor spaces.
        checkCounters(YOUNG_GENERATION, 0);
        checkCounters(YOUNG_GENERATION, 1);
        checkCounters(YOUNG_GENERATION, 2);
        // Old Generation has 1 space.
        checkCounters(OLD_GENERATION, 0);
    }
}
