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
package compiler.c2.irTests.igvn;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8314997
 * @requires vm.debug == true & vm.compiler2.enabled
 * @summary Test that diamond if-region is removed due to calling try_clean_mem_phi().
 * @library /test/lib /
 * @run driver compiler.c2.irTests.igvn.TestCleanMemPhi
 */
public class TestCleanMemPhi {
    static boolean flag, flag2;
    static int iFld;

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(0);
        testFramework.addFlags("-XX:+AlwaysIncrementalInline", "-XX:-PartialPeelLoop", "-XX:-LoopUnswitching");
        testFramework.addScenarios(new Scenario(1, "-XX:-StressIGVN"),
                                   new Scenario(2, "-XX:+StressIGVN"));
        testFramework.start();
    }

    static class A {
        int i;
    }


    static A a1 = new A();
    static A a2 = new A();


    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static void testCountedLoop() {
        int zero = 34;

        int limit = 2;
        for (; limit < 4; limit *= 2) ;
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }

        // Loop is not converted to a counted loop because a diamond is not removed due to missing to call
        // try_clean_mem_phi() again on the diamond region.
        int i = 0;
        do {
            iFld = 34;
            if (flag2) {
                iFld++;
            }

            int z = 34;
            if (flag) {
                lateInline(); // Inlined late -> leaves a MergeMem
                if (zero == 34) { // False but only cleaned up after CCP
                    iFld = 38;
                }
                z = 32; // Ensures to get a diamond If-Region
            }
            // Region merging a proper diamond after CCP with a memory phi merging loop phi and the MergeMem from lateInline().
            // Region is not added to the IGVN worklist anymore once the second phi dies.
            i++;
        } while (zero == 34 || (i < 1000 && a1 == a2)); // Could be converted to a counted loop after the diamond is removed after CCP.
    }

    @Test
    @IR(failOn = IRNode.LOOP)
    static void testRemoveLoop() {
        int zero = 34;

        int limit = 2;
        for (; limit < 4; limit *= 2) ;
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }

        // Loop is not converted to a counted loop and thus cannot be removed as empty loop because a diamond is not
        // removed due to missing to call try_clean_mem_phi() again on the diamond region.
        int i = 0;
        do {
            iFld = 34;

            int z = 34;
            if (flag) {
                lateInline(); // Inlined late -> leaves a MergeMem
                if (zero == 34) { // False but only cleaned up after CCP
                    iFld = 38;
                }
                z = 32; // Ensures to get a diamond If-Region
            }
            // Region merging a proper diamond after CCP with a memory phi merging loop phi and the MergeMem from lateInline().
            // Region is not added to the IGVN worklist anymore once the second phi dies.

            i++;
        } while (zero == 34 || (i < 21 && a1 == a2));
    }

    static void lateInline() {
    }
}
