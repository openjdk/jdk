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
 * @test
 * @bug     8368527
 * @library /test/lib
 * @summary Basic test of MemoryMXBean.getGcCpuTime
 *
 * @run main/othervm -XX:+UseSerialGC GetGcCpuTime _
 * @run main/othervm -XX:+UseParallelGC GetGcCpuTime _
 * @run main/othervm -XX:+UseG1GC GetGcCpuTime _
 * @run main/othervm -XX:+UseZGC GetGcCpuTime _
 */

import jdk.test.lib.process.OutputAnalyzer;
import static jdk.test.lib.process.ProcessTools.createTestJavaProcessBuilder;
import static jdk.test.lib.process.ProcessTools.executeProcess;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

 public class GetGcCpuTime {
    static final ThreadMXBean mxThreadBean = ManagementFactory.getThreadMXBean();
    static final MemoryMXBean mxMemoryBean = ManagementFactory.getMemoryMXBean();

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            ProcessBuilder pb = createTestJavaProcessBuilder("GetGcCpuTime");
            OutputAnalyzer output = executeProcess(pb);
            output.shouldNotContain("GC CPU time should");
            output.shouldHaveExitValue(0);
            return;
        }

        try {
            if (!mxThreadBean.isThreadCpuTimeEnabled()) {
                return;
            }
        } catch (UnsupportedOperationException e) {
            if (mxMemoryBean.getGcCpuTime() != -1) {
                throw new Error("GC CPU time should be -1");
            }
            return;
        }
        System.gc();
        long gcCpuTime = mxMemoryBean.getGcCpuTime();
        if (gcCpuTime == 0) {
            throw new Error("GC CPU time should not be zero after System.gc()");
        }

        final int numberOfThreads = 1000;
        for (int i = 0; i < numberOfThreads; i++) {
            Thread t = new Thread(() -> {
                while (true) {
                    long gcCpuTimeFromThread = mxMemoryBean.getGcCpuTime();
                    if (gcCpuTimeFromThread < -1) {
                        throw new Error("GC CPU time should never be less than -1 but was " + gcCpuTimeFromThread);
                    }
                }
            });
            t.start();
        }

        System.exit(0);
     }
 }
