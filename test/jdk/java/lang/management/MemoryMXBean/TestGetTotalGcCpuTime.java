/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=Epsilon
 * @requires vm.gc.Epsilon
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC TestGetTotalGcCpuTime
 */

/*
 * @test id=Serial
 * @requires vm.gc.Serial
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UseSerialGC TestGetTotalGcCpuTime
 */

/*
 * @test id=Parallel
 * @requires vm.gc.Parallel
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UseParallelGC TestGetTotalGcCpuTime
 */

/*
 * @test id=G1
 * @requires vm.gc.G1
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UseG1GC TestGetTotalGcCpuTime
 */

/*
 * @test id=ZGC
 * @requires vm.gc.Z
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UseZGC TestGetTotalGcCpuTime
 */

/*
 * @test id=Shenandoah
 * @requires vm.gc.Shenandoah
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UseShenandoahGC TestGetTotalGcCpuTime
 */

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;

public class TestGetTotalGcCpuTime {
    static final ThreadMXBean mxThreadBean = ManagementFactory.getThreadMXBean();
    static final MemoryMXBean mxMemoryBean = ManagementFactory.getMemoryMXBean();
    static final boolean usingEpsilonGC = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().anyMatch(p -> p.contains("-XX:+UseEpsilonGC"));

    private static ArrayList<Object> objs = null;

    public static void main(String[] args) throws Exception {
        try {
            if (!mxThreadBean.isThreadCpuTimeEnabled()) {
                return;
            }
        } catch (UnsupportedOperationException e) {
            if (mxMemoryBean.getTotalGcCpuTime() != -1) {
                throw new RuntimeException("GC CPU time should be -1");
            }
            return;
        }

        // Add some tracing work to ensure OSs with slower update rates would report usage
        for (int i = 0; i < 200; i++) {
            objs = new ArrayList<Object>();
            for (int j = 0; j < 5000; j++) {
                objs.add(new Object());
            }
            System.gc();
        }

        long gcCpuTimeFromThread = mxMemoryBean.getTotalGcCpuTime();

        if (usingEpsilonGC) {
            if (gcCpuTimeFromThread != 0) {
                throw new RuntimeException("Epsilon GC can't have any GC CPU time by definition");
            }
        } else {
            if (gcCpuTimeFromThread <= 0) {
                throw new RuntimeException("Some GC CPU time must have been reported");
            }
        }
    }
}
