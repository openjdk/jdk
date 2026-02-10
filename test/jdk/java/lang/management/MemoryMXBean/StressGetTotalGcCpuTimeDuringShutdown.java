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
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC StressGetTotalGcCpuTimeDuringShutdown
 */

/*
 * @test id=Serial
 * @requires vm.gc.Serial
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UseSerialGC StressGetTotalGcCpuTimeDuringShutdown
 */

/*
 * @test id=Parallel
 * @requires vm.gc.Parallel
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UseParallelGC StressGetTotalGcCpuTimeDuringShutdown
 */

/*
 * @test id=G1
 * @requires vm.gc.G1
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UseG1GC StressGetTotalGcCpuTimeDuringShutdown
 */

/*
 * @test id=ZGC
 * @requires vm.gc.Z
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UseZGC StressGetTotalGcCpuTimeDuringShutdown
 */

/*
 * @test id=Shenandoah
 * @requires vm.gc.Shenandoah
 * @bug     8368527
 * @summary Stress MemoryMXBean.getTotalGcCpuTime during shutdown
 * @library /test/lib
 * @run main/othervm -XX:+UseShenandoahGC StressGetTotalGcCpuTimeDuringShutdown
 */

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

public class StressGetTotalGcCpuTimeDuringShutdown {
    static final ThreadMXBean mxThreadBean = ManagementFactory.getThreadMXBean();
    static final MemoryMXBean mxMemoryBean = ManagementFactory.getMemoryMXBean();

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

        final int numberOfThreads = Runtime.getRuntime().availableProcessors() * 8;
        for (int i = 0; i < numberOfThreads; i++) {
            Thread t = new Thread(() -> {
                while (true) {
                    long gcCpuTimeFromThread = mxMemoryBean.getTotalGcCpuTime();
                    if (gcCpuTimeFromThread < -1) {
                        throw new RuntimeException("GC CPU time should never be less than -1 but was " + gcCpuTimeFromThread);
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }
    }
}
