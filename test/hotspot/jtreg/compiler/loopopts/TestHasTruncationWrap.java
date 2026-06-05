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

/*
 * @test id=vanilla
 * @bug 8385855
 * @summary Test CountedLoopConverter::has_truncation_wrap logic that checks if
 *          a truncated iv (e.g. byte or char iv) is still a valid counted loop.
 * @library /test/lib /
 * @run main ${test.main.class}
 */

/*
 * @test id=Xcomp
 * @bug 8385855
 * @library /test/lib /
 * @run main ${test.main.class} -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,${test.main.class}::test*
 */

package compiler.loopopts;

import compiler.lib.ir_framework.*;

/**
 * TODO: descr
 */
public class TestHasTruncationWrap {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addFlags(args);
        framework.start();
    }

    // ------------------------- Failing cases for JDK-8385855 ------------------------------

    // Test shape first reported in JDK-8385855, led to assert in JDK27:
    //   assert(cmp->Opcode() == Op_CmpI) failed: signed comparison required
    public static int   test0_start = 0;
    public static int   test0_stop  = 100;
    public static int[] test0_array = new int[100];

    @Test
    public static void test0() {
        int   start = test0_start;
        int   stop  = test0_stop;
        int[] array = test0_array;

        stop = (stop << 16) >> 16;
        int v = array[start]; // dominating CmpU detected by filtered_int_type
        for (int i = start; i < stop;) {
            i++;
            i = (i << 16) >> 16; // iv truncation
        }
    }

    // ---- More general tests, Checking that truncated iv loops become CountedLoops ---------

    // TODO: replace with real test!
    //@Test
    //@IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    //@IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    //@Arguments(values = {Argument.NUMBER_42})
    //public static void test_lohi_ltle(int i) {
    //    if (i < -100_000 || i > 100_000) {
    //        throw new RuntimeException();
    //    }
    //}
}
