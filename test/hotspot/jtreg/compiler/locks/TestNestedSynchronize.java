/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8322996 8324839 8325467
 * @summary Ensure C2 can compile deeply nested synchronize statements.
 *          Exercises C2 register masks, in particular. We incrementally
 *          increase the level of nesting (up to 100) to trigger potential edge
 *          cases.
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.locks.TestNestedSynchronize::test*
 *                   -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+AbortVMOnCompilationFailure
 *                   compiler.locks.TestNestedSynchronize
 */

package compiler.locks;

public class TestNestedSynchronize {
    public static void main(String[] args) {
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
        test11();
        test12();
        test13();
        test14();
        test15();
        test16();
        test17();
        test18();
        test19();
        test20();
        test21();
        test22();
        test23();
        test24();
        test25();
        test26();
        test27();
        test28();
        test29();
        test30();
        test31();
        test32();
        test33();
        test34();
        test35();
        test36();
        test37();
        test38();
        test39();
        test40();
        test41();
        test42();
        test43();
        test44();
        test45();
        test46();
        test47();
        test48();
        test49();
        test50();
        test51();
        test52();
        test53();
        test54();
        test55();
        test56();
        test57();
        test58();
        test59();
        test60();
        test61();
        test62();
        test63();
        test64();
        test65();
        test66();
        test67();
        test68();
        test69();
        test70();
        test71();
        test72();
        test73();
        test74();
        test75();
        test76();
        test77();
        test78();
        test79();
        test80();
        test81();
        test82();
        test83();
        test84();
        test85();
        test86();
        test87();
        test88();
        test89();
        test90();
        test91();
        test92();
        test93();
        test94();
        test95();
        test96();
        test97();
        test98();
        test99();
        test100();
    }
    public static void test1() {
        synchronized (TestNestedSynchronize.class) {
        }
    }

    public static void test2() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
    }

    public static void test3() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
    }

    public static void test4() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
    }

    public static void test5() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
    }

    public static void test6() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
    }

    public static void test7() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test8() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test9() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test10() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test11() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test12() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test13() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test14() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test15() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test16() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test17() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test18() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test19() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test20() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test21() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test22() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test23() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test24() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test25() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test26() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test27() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test28() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test29() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test30() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test31() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test32() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test33() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test34() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test35() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test36() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test37() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test38() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test39() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test40() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test41() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test42() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test43() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test44() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test45() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test46() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test47() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test48() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test49() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test50() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test51() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test52() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test53() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test54() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test55() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test56() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test57() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test58() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test59() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test60() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test61() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test62() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test63() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test64() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test65() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test66() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test67() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test68() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test69() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test70() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test71() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test72() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test73() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test74() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test75() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test76() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test77() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test78() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test79() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test80() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test81() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test82() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test83() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test84() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test85() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test86() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test87() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test88() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test89() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test90() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test91() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test92() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test93() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test94() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test95() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test96() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test97() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test98() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test99() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }

    public static void test100() {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        synchronized (TestNestedSynchronize.class) {
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
    }
}
