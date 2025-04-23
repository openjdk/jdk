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

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8352587
 * @summary IR test to ensure that PeelMainPost cases does not get Multiversioned.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestPeelMainPostNoMultiversioning
 */

public class TestPeelMainPostNoMultiversioning {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestPeelMainPostNoMultiversioning.class);
        // No traps means we cannot use the predicates version for SuperWord / AutoVectorization,
        // and instead use multiversioning directly.
        framework.addFlags("-XX:-TieredCompilation", "-XX:PerMethodTrapLimit=0");
        framework.setDefaultWarmup(0); // simulates Xcomp
        framework.start();
    }

    public static long value = 1;
    public static long multiplicator = 3;

    @Test
    @IR(counts = {".*multiversion.*", "= 0"},
        phase = CompilePhase.PHASEIDEALLOOP1)
    @IR(counts = {".*multiversion.*", "= 0"},
        phase = CompilePhase.PHASEIDEALLOOP_ITERATIONS)
    @IR(counts = {".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL)
    // We are checking it for a few phases, just to make sure we are not seeing multiversioning
    // at any point.
    public static void test() {
        long x = value;
        long y = multiplicator;
        for (int i = 0; i < 10_000; i++) {
            x *= y; // No memory load/store -> PeelMainPost
        }
        value = x;
    }
}
