/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @bug 8297345
 * @summary C2: SIGSEGV in PhaseIdealLoop::push_pinned_nodes_thru_region
 * @requires vm.gc.Parallel
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileOnly=TestCheckCastPPBecomesTOP::test1 -XX:LoopMaxUnroll=0
 *                   -XX:CompileCommand=dontinline,TestCheckCastPPBecomesTOP::notInlined -XX:+UseParallelGC TestCheckCastPPBecomesTOP
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileOnly=TestCheckCastPPBecomesTOP::test1 -XX:LoopMaxUnroll=0
 *                   -XX:CompileCommand=dontinline,TestCheckCastPPBecomesTOP::notInlined -XX:+UseParallelGC -XX:-UseCompressedClassPointers TestCheckCastPPBecomesTOP
 *
 */

public class TestCheckCastPPBecomesTOP {
    private static I field;
    private static I field2;
    private static I field3;
    private static volatile int barrier;

    public static void main(String[] args) {
        A a = new A();
        B b = new B();
        for (int i = 0; i < 100_000; i++) {
            test1Helper3(5);
            field2 = field = a;
            test1Helper1(b, 100, 100);
            test1Helper1(b, 100, 100);
            test1Helper1(b, 100, 100);
            field2 = field = b;
            test1Helper1(b, 100, 100);
            test1Helper1(b, 100, 100);

            field2 = field = a;
            test1Helper1(b, 10, 100);
            test1Helper1(b, 10, 100);
            test1Helper1(b, 10, 100);
            field2 = field = b;
            test1Helper1(b, 10, 100);
            test1Helper1(b, 10, 100);

            field2 = field = a;
            test1Helper1(b, 10, 10);
            test1Helper1(b, 10, 10);
            test1Helper1(b, 10, 10);
            field2 = field = b;
            test1Helper1(b, 10, 10);
            test1Helper1(b, 10, 10);

            field2 = field = a;
            test1Helper2(b, true);
            field2 = field = b;
            test1Helper2(b, true);

            test1(false);
        }
   }


    private static void test1(boolean flag1) {
        I f = field;
        if (f == null) {
        }
        test1Helper3(10);
        test1Helper2(f, flag1);

            for (int j = 0; j < 10; j++) {
                for (int k = 0; k < 10; k++) {
                    for (int l = 0; l < 10; l++) {

                    }
                }
            }
    }

    private static void test1Helper3(int stop) {
        int i;
        for (i = 0; i < stop; i++) {

        }
        if (i != 10) {
            barrier = 0x42;
        }
    }


    private static void test1Helper2(I f2, boolean flag1) {
        if (flag1) {
            if (f2 == null) {

            }
            int i;
            for (i = 0; i < 10; i++) {
            }
            int j;
            for (j = 0; j < 10; j++) {
                for (int k = 0; k < 10; k++) {

                }
            }
            test1Helper1(f2, i, j);
        }
    }

    private static void test1Helper1(I f2, int i, int j) {
        I f1 = field;
        if (f1 == null) {

        }
        I f3 = field2;
        if (f3 == null) {
        }
        field2 = f3;
        field = f1;
        if (i == 10) {
            if (j == 10) {
                f1.m1();
            } else {
                f1 = f3;
            }
            f3.m2(f1);
        } else {
            f1 = f3;
        }
        I f4 = field2;
        field = f1;
        f4.m3(f1, f2);
        I f5 = field;
        barrier = 0x42;
        f5.m4(f2);
    }

    private static void notInlined(Object o1, Object o2) {

    }

    interface I {
        void m1();
        void m2(I f);
        void m3(I f1, I f2);

        void m4(I f2);
    }

    static class A implements I {
        public void m1() {

        }

        public void m2(I f) {
            f.m1();
        }

        public void m3(I f1, I f2) {
            f1.m1();
            f2.m1();
        }

        public void m4(I f2) {
            notInlined(this, f2);
            field3 = this;
        }
    }

    static class B implements I {
        public void m1() {

        }

        public void m2(I f) {
            f.m1();
        }

        public void m3(I f1, I f2) {
            f1.m1();
            f2.m1();
        }

        public void m4(I f2) {
            notInlined(this, f2);
            field3 = this;
        }

    }
}
