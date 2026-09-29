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

package gc.z;

/*
 * @test TestMappedCacheHarvest
 * @requires vm.gc.Z
 * @summary Test ZGC mapped cache harvesting
 * @library /test/lib
 * @run driver gc.z.TestMappedCacheHarvest
 */

import java.util.ArrayList;
import java.lang.instrument.Instrumentation;
import jdk.test.lib.process.ProcessTools;

public class TestMappedCacheHarvest {
    static class Test {
        private static int M = 1024 * 1024;

        private static int LARGE = 33 * M;
        private static int MEDIUM = 3 * M;

        public volatile static byte[] tmp;

        private static long freeMem() {
            long currentlyCommitted = Runtime.getRuntime().totalMemory();
            long currentlyFree = Runtime.getRuntime().freeMemory();
            long max = Runtime.getRuntime().maxMemory();
            return max - (currentlyCommitted - currentlyFree);
        }

        public static void main(String[] args) throws Exception {
            int iter = 0;
            ArrayList<byte[]> keep = new ArrayList<>();
            try {
                // Interleave ever growing different sized allocation while
                // keeping the smaller allocations alive until the heap is full
                // and having the larger allocations be transient.
                while (freeMem() > LARGE) {
                    tmp = new byte[LARGE];
                    keep.add(new byte[MEDIUM]);

                    System.gc();
                    LARGE += 2*M;
                    if (freeMem() < LARGE) {
                        // Release the keep to see if we can continue
                        keep = new ArrayList<>();
                        System.gc();
                        MEDIUM += 2*M;
                    }
                }
                System.out.println("Last large size: " + LARGE / M + "M (" + iter + ") Free mem: " +
                        Runtime.getRuntime().freeMemory() / M + "M" );
            } catch (OutOfMemoryError oome) {
                keep = null;
                System.out.println("Premature OOME: large size: " + LARGE / M + "M (" + iter + ") Free mem: " +
                        Runtime.getRuntime().freeMemory() / M + "M" );
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ProcessTools.executeTestJava(
            "-XX:+UseZGC",
            "-Xms128M",
            "-Xmx128M",
            "-Xlog:gc,gc+init,gc+heap=debug",
            Test.class.getName())
                .outputTo(System.out)
                .errorTo(System.out)
                .shouldContain("Mapped Cache Harvested:")
                .shouldNotContain("Out of address space")
                .shouldHaveExitValue(0);
    }
}
