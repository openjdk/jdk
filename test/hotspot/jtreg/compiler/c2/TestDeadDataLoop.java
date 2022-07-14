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

/*
 * @test
 * @bug 8284358
 * @summary An unreachable loop is not removed, leading to a broken graph.
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=1448005075
 *                   -XX:CompileCommand=compileonly,*TestDeadDataLoop::test* -XX:CompileCommand=dontinline,*TestDeadDataLoop::notInlined
 *                   compiler.c2.TestDeadDataLoop
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=1922737097
 *                   -XX:CompileCommand=compileonly,*TestDeadDataLoop::test* -XX:CompileCommand=dontinline,*TestDeadDataLoop::notInlined
 *                   compiler.c2.TestDeadDataLoop
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *                   -XX:CompileCommand=compileonly,*TestDeadDataLoop::test* -XX:CompileCommand=dontinline,*TestDeadDataLoop::notInlined
 *                   compiler.c2.TestDeadDataLoop
 */

package compiler.c2;

public class TestDeadDataLoop {

    static class MyValue {
        final int x;

        MyValue(int x) {
            this.x = x;
        }
    }

    static boolean flag = false;
    static MyValue escape = null;
    static volatile int volInt = 0;

    static boolean test1() {
        Integer box;
        if (flag) {
            box = 0;
        } else {
            box = 1;
        }
        if (box == 2) {
            // Not reachable but that's only known after Incremental Boxing Inline
            for (int i = 0; i < 1000;) {
                if (notInlined()) {
                    break;
                }
            }
            MyValue val = new MyValue(4);

            escape = new MyValue(42);

            // Trigger scalarization of val in safepoint debug info
            notInlined();
            if (val.x < 0) {
              return true;
            }
        }
        return false;
    }

    static boolean test2() {
        MyValue box = new MyValue(1);
        if (box.x == 0) {
            // Not reachable but that's only known once the box.x load is folded during IGVN
            for (int i = 0; i < 1000;) {
                if (notInlined()) {
                    break;
                }
            }
            MyValue val = new MyValue(4);

            escape = new MyValue(42);

            // Trigger scalarization of val in safepoint debug info
            notInlined();
            if (val.x < 0) {
              return true;
            }
        }
        return false;
    }

    static void test3() {
        Integer box = 0;
        if (flag) {
            box = 1;
        }
        if (box == 2) {
            for (int i = 0; i < 1000;) {
                if (notInlined()) {
                    break;
                }
            }
            escape = new MyValue(42);
        }
    }

    static void test4() {
        MyValue box = new MyValue(1);
        if (box.x == 0) {
            for (int i = 0; i < 1000;) {
                if (notInlined()) {
                    break;
                }
            }
            escape = new MyValue(42);
        }
    }

    static void test5() {
        MyValue box = new MyValue(1);
        if (box.x == 0) {
            while (true) {
                if (notInlined()) {
                    break;
                }
            }
            escape = new MyValue(42);
        }
    }

    static void test6() {
        MyValue box = new MyValue(1);
        if (box.x == 0) {
            while (notInlined()) { }
            escape = new MyValue(42);
        }
    }

    static void test7() {
        MyValue box = new MyValue(1);
        if (box.x == 0) {
            while (true) {
                escape = new MyValue(2);
                if (notInlined()) {
                    break;
                }
            }
            escape = new MyValue(42);
        }
    }

    static void test8() {
        MyValue box = new MyValue(1);
        if (box.x == 0) {
            for (int i = 0; i < 1000;) {
                notInlined();
                if (flag) {
                    break;
                }
            }
            escape = new MyValue(42);
        }
    }

    static boolean test9() {
        Integer box;
        if (flag) {
            box = 0;
        } else {
            box = 1;
        }
        if (box == 2) {
            for (int i = 0; i < 1000;) {
                // MemBarRelease as only Phi user
                volInt = 42;
                if (flag) {
                    break;
                }
            }
            MyValue val = new MyValue(4);

            escape = new MyValue(42);

            notInlined();
            if (val.x < 0) {
              return true;
            }
        }
        return false;
    }

    static void test10() {
        MyValue box = new MyValue(1);
        if (box.x == 0) {
            while (true) {
                // Allocate node as only Phi user
                escape = new MyValue(2);
                if (flag) {
                    break;
                }
            }
            escape = new MyValue(42);
        }
    }

    public static boolean notInlined() {
        return false;
    }

    public static void main(String[] args) {
        // Make sure classes are initialized
        Integer i = 42;
        new MyValue(i);
        test1();
        test2();
        test3();
        test4();
        test5();
        test6();
        test7();
        test8();
        test9();
        test10();
    }
}

