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
/*
 * @test
 * @bug 8303737
 * @summary C2: Load can bypass subtype check that enforces it's from the right object type
 * @requires vm.gc.Parallel
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:CompileOnly=TestLoadBypassesClassCast::test
 *                   -XX:CompileThreshold=20000 -XX:LoopMaxUnroll=1 -XX:-LoopUnswitching -XX:+UseParallelGC TestLoadBypassesClassCast
 *
 */

public class TestLoadBypassesClassCast {
    private static Object saved_o;
    private static Object field_o = new A();
    private static Object saved_casted_o;
    private static float barrier;
    private static Object[] memory = new Object[100];

    public static void main(String[] args) {
        float[] array = new float[100];
        A a = new A();
        B b = new B();
        C c = new C();
        D d = new D();

        // create garbage so GC runs
        Thread thread = new Thread() {
            public void run() {
                while (true) {
                    int[] array = new int[1000];
                }
            }
        };

        thread.setDaemon(true);
        thread.start();

        for (int i = 0; i < 20_000; i++) {
            test(true, a, array, true, false);
            test(false, b, array, true, false);
            test(false, d, array, true, true);
            test(true, a, array, false, false);
            test(false, b, array, false, false);
            testHelper2(42);
            testHelper3(true, 42);
        }
        for (int j = 0; j < 1000; j++) {
            for (int i = 0; i < 1_000_000; i++) {
                test(false, d, array, true, true);
            }
        }
    }

    private static int test(boolean flag, Object o, float[] array, boolean flag2, boolean flag3) {
        int ret = (int)array[2];
        if (o == null) {
        }
        saved_o = o;  // (CastPP o): cast to not null

        // A.objectField load from o hosted here even though o was not checked to be of type A
        // result of the load doesn't hold an oop if o is not an A
        if (flag2) {
            for (int i = 1; i < 100; i *= 2) {
                // safepoint here with result of load above live and expected to be an oop. Not the case
                // if o is of type D: crash in gc code
            }

            if (flag3) {
            } else {
                saved_casted_o = (A) o;  // (CheckCastPP (CastPP o)): cast to not null A

                int j;
                for (j = 1; j < 2; j *= 2) {

                }

                testHelper3(flag, j);  // goes away after CCP

                int i;
                for (i = 0; i < 2; i++) {
                }
                 // array[2] after one round of loop opts, control
                 // dependent on range check, range check replaced by
                 // array[2] range check above, control dependent
                 // nodes become control dependent on that range check
                ret += array[i];

                Object o2;
                if (flag) {
                    o2 = saved_casted_o; // (CheckCastPP (CastPP o)): cast to to not null A
                } else {
                    o2 = testHelper2(i); // (CastPP o) after 1 round of loop opts: cast to not null
                }
                // subtype check split thru Phi. CheckCastPP becomes control dependent on merge point
                // phi becomes (CastPP o) after 1 round of loop opts: cast to not null
                // subtype check from split thru phi in one branch of the if replaced by dominating one
                // empty if blocks, if goes away. CheckCastPP becomes control dependent on range check above
                // CastPP replaced by dominating CastPP for null check
                A a = (A) o2;
                ret += a.objectField.intField;
            }
        } else {
            // same logic as above so if this a.objectField load and
            // the one above lose their dependency on the type check
            // they common above all ifs
            saved_casted_o = (A) o;

            int j;
            for (j = 1; j < 2; j *= 2) {

            }

            testHelper3(flag, j);

            int i;
            for (i = 0; i < 2; i++) {
            }
            ret += array[i];

            Object o2;
            if (flag) {
                o2 = saved_casted_o;
            } else {
                o2 = testHelper2(i);
            }
            A a = (A) o2;
            ret += a.objectField.intField;
            ret += barrier;
        }

        return ret;
    }

    private static void testHelper3(boolean flag, int j) {
        if (j == 2) {
            if (flag) {
                barrier = 42;
            }
        }
    }

    private static Object testHelper2(int i) {
        Object o2;
        if (i == 2) {
            o2 = saved_o;
        } else {
            o2 = field_o;
            if (o2 == null) {
            }
        }
        return o2;
    }

    private static class C {
    }

    private static class A extends C {
        public E objectField = new E();
    }

    private static class B extends A {
    }

    private static class D extends C {
        public int neverAccessedField = 0x12345678;

    }

    private static class E {
        public int intField;
    }

}
