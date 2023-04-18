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
 *
 */

/*
 * @test
 * @bug 8280126
 * @compile TestDeadIrreducibleLoops.jasm
 * @summary Irreducible loops have many entries, only when the last entry loses
 *          control from the outside does the loop die, and have to disappear.
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,TestDeadIrreducibleLoopsMain::test*
 *      -XX:CompileCommand=compileonly,TestDeadIrreducibleLoops::test*
 *      -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *      TestDeadIrreducibleLoopsMain
 */

/*
 * @test
 * @bug 8280126
 * @compile TestDeadIrreducibleLoops.jasm
 * @summary Irreducible loops have many entries, only when the last entry loses
 *          control from the outside does the loop die, and have to disappear.
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,TestDeadIrreducibleLoopsMain::test*
 *      -XX:CompileCommand=compileonly,TestDeadIrreducibleLoops::test*
 *      -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *      -XX:PerMethodTrapLimit=0
 *      TestDeadIrreducibleLoopsMain
 */

/*
 * @test
 * @bug 8280126
 * @compile TestDeadIrreducibleLoops.jasm
 * @summary Irreducible loops have many entries, only when the last entry loses
 *          control from the outside does the loop die, and have to disappear.
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,TestDeadIrreducibleLoopsMain::test*
 *      -XX:CompileCommand=compileonly,TestDeadIrreducibleLoops::test*
 *      -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *      -Xcomp -XX:-TieredCompilation
 *      TestDeadIrreducibleLoopsMain
 */

/*
 * @test
 * @bug 8280126
 * @compile TestDeadIrreducibleLoops.jasm
 * @summary Irreducible loops have many entries, only when the last entry loses
 *          control from the outside does the loop die, and have to disappear.
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,TestDeadIrreducibleLoopsMain::test*
 *      -XX:CompileCommand=compileonly,TestDeadIrreducibleLoops::test*
 *      -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:PerMethodTrapLimit=0
 *      TestDeadIrreducibleLoopsMain
 */

// Note: if this test fails intermittently, then use -XX:RepeatCompilation=1000
// The tests are run in no particular order. If an earlier test fails, a later one
// may fail too and be easier to debug.

public class TestDeadIrreducibleLoopsMain {
   static public void main(String[] args) {
        TestDeadIrreducibleLoops t = new TestDeadIrreducibleLoops();
        test_000(false, false);
        t.test_001(0, 0, 0, 0);
        t.test_002(-1);
        t.test_003(255);
        t.test_004("I am an object\n");
        t.test_005(0, 0);
        t.test_006(0);
        t.test_007(0, 0, 0);
        t.test_008(0, 0, 0);
        t.test_009(0, 0, 0, 0, 0, 0, 0);
        t.test_010(0, 0, 0, 0, 0);
        t.test_011(0, 0, 0, 0, 0);
        t.test_012a(0, 0, 0, 0, 0, 0, 0, 0);
        t.test_012b(0, 0, 0, 0, 0);
        t.test_013(0, 0, 0, 0, 0, 0, 0, 0, 0);
        t.test_014a(0, 0, 0);
        t.test_014b(0, 0, 0);
        int x = t.test_015a(123);
        int y = t.test_015b(123);
        assert x == y: "pow(3,x)";
        t.test_016(0, 0, 0);
        t.test_017(0, 0, 0);
        t.test_018(0);
        t.test_019(0, 0, 0, 0);
        t.test_020(0, 0, 0, 0, 0);
        t.test_021(0, 0, 0, 0, 0, 0);
        t.test_022a(0, 0, 0, 0);
        t.test_022b(0, 0);
        t.test_023(0);
        t.test_024();
        test_025a(false);
        test_025b(false, false);
    }

    public static float test_000(boolean flag1, boolean flag2) {
        float ret = 1.0f;
        int x = 0;
        LOOP1:
        for (int i = 1; i < 1000000; i *= 2) { // about 20 iterations
            if (i % 5 != 0) { // SKIP1
                LOOP2:
                for (int j = 1; j < 1000000; j *= 2) { // about 20 iterations
                    if (j % 5 != 0) { // SKIP2
                        if (x == 0) { // eventually always false -> continue statements float out of loop
                            ret *= 1.0001;
                            if (j > 100) {
                                LOOP3:
                                for (float m = 1.0f; m < 30000.0f; m *= 1.0001f) {
                                    // OSR starts here - should do more than 100k iterations
                                    ret *= 0.99999f;
                                }
                                x = 1;
                            }
                            int y = 77;
                            for (int e = 0; e < 77; e++) {
                                y -= x; // empty_loop, once we know that x == 1
                            }
                            if (y == 0) {
                                // always true after OSR -> cut off ENTRY1 and ENTRY2
                                return ret;
                            }
                            ret += 0.01;
                            if (ret > 20000) {
                                ret = 7.0f;
                                continue LOOP1; // ENTRY1
                            }
                            // back to LOOP2 -> ENTRY2
                        } // end if (x == 0)
                    } // end SKIP2
                } // end LOOP2
            } // end SKIP1
        } // end LOOP1
        return ret;
    }

    static float test_025a(boolean flag) {
        // Based on test_000, but much simplified.
        // Irreducible loop with OSR. Inlining in irreducible loop.
        float ret = 3.0f;
        LOOP1:
        for (long i = 1; i < 1000_000_000_000L; i *= 2) {
            ret = test_025_inline(ret); // inline region
            LOOP2:
            for (long j = 1; j < 1000_000_000_000L; j *= 2) {
                for (int e = 0; e < 77; e++) {}
                if (flag) {
                    continue LOOP1; // ENTRY1
                }
                // back to LOOP2 -> ENTRY2
            } // end LOOP2
        } // end LOOP1
        return ret;
    }

    static float test_025b(boolean flag1, boolean flag2) {
        // Based on test_000.
        // Irreducible loop with OSR. Inlining in irreducible loop.
        float ret = 1.0f;
        int x = 0;
        LOOP1:
        for (long i = 1; i < 1000_000_000_000L; i *= 2) {
            ret = test_025_inline(ret);
            if (i % 5 != 0) { // SKIP1
                LOOP2:
                for (long j = 1; j < 1000_000_000_000L; j *= 2) {
                    if (j % 5 != 0) { // SKIP2
                        if (x == 0) { // eventually always false -> continue statements float out of loop
                            ret *= 1.0001;
                            if (i > 1000_000_000L) {
                                LOOP3:
                                for (float m = 1.0f; m < 30000.0f; m *= 1.0001f) {
                                    // OSR starts here - should do more than 100k iterations
                                    ret *= 0.99999f;
                                }
                                x = 1;
                            }
                            int y = 77;
                            for (int e = 0; e < 77; e++) {
                                y -= x; // empty_loop, once we know that x == 1
                            }
                            if (y == 0) {
                                // always true after OSR -> cut off ENTRY1 and ENTRY2
                                return ret;
                            }
                            ret += 0.01;
                            if (ret > 20000) {
                                ret = 7.0f;
                                continue LOOP1; // ENTRY1
                            }
                            // back to LOOP2 -> ENTRY2
                        } // end if (x == 0)
                    } // end SKIP2
                } // end LOOP2
            } // end SKIP1
        } // end LOOP1
        return ret;
    }

    static float test_025_inline(float x) {
        if (x >= 1.0f) {
          x *= 0.5f;
        } else {
          x *= 2.0f;
        }
        // Region to merge the if
        return x;
    }
}
