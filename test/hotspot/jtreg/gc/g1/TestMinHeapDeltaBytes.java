/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package gc.g1;

/*
 * @test
 * @bug 8394507
 * @summary G1 heap expansion must not truncate a large region count.
 * @requires vm.gc.G1 & (vm.bits == "64")
 * @library /test/lib
 * @run driver gc.g1.TestMinHeapDeltaBytes
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;

public class TestMinHeapDeltaBytes {
    public static void main(String[] args) throws Exception {
        ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                           "-Xms8m", "-Xmx512m",
                                           "-XX:MinHeapDeltaBytes=9223372036854775808",
                                           "-XX:+PrintFlagsFinal",
                                           Allocation.class.getName())
                    .shouldHaveExitValue(0)
                    .shouldContain("MinHeapDeltaBytes (9223372036854775808) is greater than MaxHeapSize (536870912)")
                    .shouldMatch("MinHeapDeltaBytes\\s+:?=\\s+536870912\\s")
                    .shouldContain("Expected allocation failure");
    }

    static class Allocation {
        public static void main(String[] args) {
            Runtime runtime = Runtime.getRuntime();
            Asserts.assertLessThan(runtime.totalMemory(), runtime.maxMemory());
            try {
                // Leave insufficient room for the allocation, forcing expansion recovery.
                byte[] array = new byte[Math.toIntExact(runtime.maxMemory() - 64)];
                throw new RuntimeException("Unexpected allocation of " + array.length + " bytes");
            } catch (OutOfMemoryError expected) {
                System.out.println("Expected allocation failure");
            }
        }
    }
}
