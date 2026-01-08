/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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
 * @bug 8289996
 * @summary Test range check hoisting for some scaled iv at array index
 * @library /test/lib /
 * @requires vm.flagless
 * @requires vm.debug & vm.compiler2.enabled
 * @requires os.simpleArch == "x64" | os.arch == "aarch64" | (os.arch == "riscv64" & vm.cpu.features ~= ".*rvv.*")
 * @modules jdk.incubator.vector
 * @run main/othervm compiler.rangechecks.TestRangeCheckHoistingScaledIV
 */

package compiler.rangechecks;

import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestRangeCheckHoistingScaledIV {

    // Inner class for test loops
    class Launcher {
        private static final int SIZE = 16000;
        private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_64;
        private static final ByteOrder ORDER = ByteOrder.nativeOrder();

        private static byte[] ta = new byte[SIZE];
        private static byte[] tb = new byte[SIZE];

        private static MemorySegment sa = MemorySegment.ofArray(ta);
        private static MemorySegment sb = MemorySegment.ofArray(tb);

        private static int count = 789;

        // Normal array accesses with int range checks
        public static void scaledIntIV() {
            for (int i = 0; i < count; i += 2) {
                tb[7 * i] = ta[3 * i];
            }
        }

        // Memory segment accesses with long range checks
        public static void scaledLongIV() {
            for (long l = 0; l < count; l += 64) {
                ByteVector v = ByteVector.fromMemorySegment(SPECIES, sa, l * 6, ORDER);
                v.intoMemorySegment(sb, l * 15, ORDER);
            }
        }

        public static void main(String[] args) {
            for (int i = 0; i < 20000; i++) {
                scaledIntIV();
                scaledLongIV();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "--add-modules", "jdk.incubator.vector",
                "-Xbatch", "-XX:+TraceLoopPredicate", Launcher.class.getName());
        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        analyzer.outputTo(System.out);

        // Check if int range checks are hoisted
        analyzer.stdoutShouldContain("rc_predicate init * 3");
        analyzer.stdoutShouldContain("rc_predicate init * 7");

        // Check if long range checks are hoisted
        analyzer.stdoutShouldContain("rc_predicate init * 6");
        analyzer.stdoutShouldContain("rc_predicate init * 15");
    }
}
