/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorapi;

import jdk.incubator.vector.IntVector;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8276064 8271600
 * @summary Verify that CheckCastPPs with raw oop inputs are not floating below a safepoint.
 * @library /test/lib
 * @modules jdk.incubator.vector
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=compileonly,compiler.vectorapi.TestRawOopAtSafepoint::test*
 *                   -XX:CompileCommand=dontinline,compiler.vectorapi.TestRawOopAtSafepoint::safepoint
 *                   compiler.vectorapi.TestRawOopAtSafepoint
 */
public class TestRawOopAtSafepoint {

    static int iFld = 42;

    public static void safepoint(boolean gc) {
        if (gc) {
            // Give the GC a chance to move the IntVector object on the heap
            // and thus invalidate the oop if it's not in the oopMap.
            System.gc();
        }
    }

    // Loop unswitching moves a CheckCastPP out of a loop such that the raw oop
    // input crosses a safepoint. We then SIGSEGV after the GC moved the IntVector
    // object when deferencing the now stale oop.
    public static IntVector test1(boolean flag, boolean gc) {
        IntVector vector = null;
        for (int i = 0; i < 100; i++) {
            // Trigger loop unswitching
            if (flag) {
                iFld++;
            }
            // Allocate an IntVector that will be scalarized in the
            // safepoint debug info but not in the return.
            vector = IntVector.zero(IntVector.SPECIES_MAX);
            safepoint((i == 99) && gc);
        }
        return vector;
    }

    // Same as test1 but we hit an assert in OopFlow::build_oop_map instead.
    public static IntVector test2(boolean flag, boolean gc) {
        for (int i = 0; i < 100; i++) {
            // Trigger loop unswitching
            if (flag) {
                iFld++;
            }
            IntVector vector = IntVector.zero(IntVector.SPECIES_MAX);
            safepoint((i == 99) && gc);
            if (flag) {
                return vector;
            }
        }
        return IntVector.zero(IntVector.SPECIES_MAX);
    }

    // Same as test1 but PhaseIdealLoop::try_sink_out_of_loop moves the CheckCastPP.
    public static IntVector test3(boolean flag, boolean gc) {
        IntVector vector = null;
        for (int i = 0; i < 10; i++) {
            vector = IntVector.zero(IntVector.SPECIES_MAX);
            safepoint((i == 9) && gc);
        }
        return vector;
    }

    // Same as test2 but PhaseIdealLoop::try_sink_out_of_loop moves the CheckCastPP.
    public static IntVector test4(boolean flag, boolean gc) {
        IntVector vector = null;
        for (int i = 0; i < 2; i++) {
            vector = IntVector.zero(IntVector.SPECIES_MAX);
            safepoint((i == 9) && gc);
        }
        return vector;
    }

    public static void main(String[] args) {
        int sum = 0;
        for (int i = 0; i < 15_000; ++i) {
            boolean flag = ((i % 2) == 0);
            boolean gc = (i > 14_500);
            IntVector vector1 = test1(flag, gc);
            sum += vector1.lane(0);

            IntVector vector2 = test2(flag, gc);
            sum += vector2.lane(0);

            IntVector vector3 = test3(flag, gc);
            sum += vector3.lane(0);

            IntVector vector4 = test4(flag, gc);
            sum += vector4.lane(0);
        }
        Asserts.assertEQ(sum, 0);
    }
}
