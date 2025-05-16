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

/*
 * @test
 * @bug 8327012 8327963
 * @summary Revealed issue where hook_memory_on_init links some array slice to the rawptr slice.
 *          Now that array slice depends on the rawslice. And then when the Initialize MemBar gets
 *          removed in expand_allocate_common, the rawslice sees that it has now no effect, looks
 *          through the MergeMem and sees the initial stae. That way, also the linked array slice
 *          goes to the initial state, even if before the allocation there were stores on the array
 *          slice. This leads to a messed up memory graph, and missing stores in the generated code.
 *
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.macronodes.TestEliminationOfAllocationWithoutUse::test*
 *                   compiler.macronodes.TestEliminationOfAllocationWithoutUse
 * @run main/othervm -Xcomp
 *                   -XX:CompileCommand=compileonly,compiler.macronodes.TestEliminationOfAllocationWithoutUse::test*
 *                   compiler.macronodes.TestEliminationOfAllocationWithoutUse
 */

package compiler.macronodes;

public class TestEliminationOfAllocationWithoutUse {

    static public void main(String[] args) {
        int failures = 0;
        failures += run1();
        failures += run2();
        failures += run3();
        failures += run4();
        failures += run5();
        failures += run6();
        failures += run7();
        failures += run8();
        failures += run9();
        if (failures != 0) {
            throw new RuntimeException("Had test failures: " + failures);
        }
    }

    static public int run1() {
        int size = 10;
        double[] arr1 = new double[size];
        double[] arr2 = new double[size];
        test1(arr1, arr2);

        double sum = 0;
        for (int i = 0; i < arr1.length; ++i) {
            sum += arr1[i] - arr2[i];
        }

        if (sum != (double)(size)) {
            System.out.println("test1: wrong result: " + sum + " vs expected: " + size);
            return 1;
        }
        return 0;
    }

    // Simplified from JDK-8327012 regression test.
    public static void test1(double[] arr1, double[] arr2) {
        for(int i = 0; i < arr1.length; ++i) {
            // stores on double[] slice
            arr1[i] = (double)(i + 2);
            arr2[i] = (double)(i + 1);
            // Allocation without use: but Initialize MemBar tangles the rawptr and double[] slices
            double[] tmp = new double[100];
            // When the Initialize MemBar is removed, the rawptr slice sees that there is no effect
            // and takes the initial state. The double[] slice is hooked on to the rawptr slice, and
            // also thinks it has the initial state, ignoring the double[] stores above.
        }
    }

    static public int run2() {
        int size = 10;
        double[] arr1 = new double[size];
        test2(arr1);

        double sum = 0;
        for(int i = 0; i < arr1.length; ++i) {
            sum += arr1[i];
        }

        if (sum != (double)(size)) {
            System.out.println("test2: wrong result: " + sum + " vs expected: " + size);
            return 1;
        }
        return 0;
    }

    // Simplified from test1
    public static void test2(double[] arr1) {
        for(int i = 0; i < arr1.length; ++i) {
            arr1[i] = 1;
            double[] tmp = new double[100];
        }
    }

    static public int run3() {
        int size = 10;
        int[] arr1 = new int[size];
        test3(arr1);

        int sum = 0;
        for(int i = 0; i < arr1.length; ++i) {
            sum += arr1[i];
        }

        if (sum != size) {
            System.out.println("test3: wrong result: " + sum + " vs expected: " + size);
            return 1;
        }
        return 0;
    }

    // Modified from test2
    public static void test3(int[] arr1) {
        for(int i = 0; i < arr1.length; ++i) {
            arr1[i] = 1;
            int[] tmp = new int[100];
        }
    }

    // From TestIncorrectResult.java in JDK-8324739
    static int test4(int l2) {
       int[] tmp = new int[20];

       for (int j = 0; j < l2; ++j) {
           tmp[j] = 42;
           int[] unused_but_necessary = new int[400];
       }

       return tmp[0];
    }

    public static int run4() {
        for (int i = 0; i < 100; ++i) {
            long res = test4(20);

            if (res != 42) {
                System.out.println("test4: wrong result: " + res + " vs expected: 42");
                return 1;
            }
        }
        return 0;
    }

    // From JDK-8336701
    static class Test5 {
        int[] b = new int[400];
        static int[] staticArray = new int[400];
    }

    static void test5() {
        long e;
        for (e = 1; e < 9; ++e) {
            Test5.staticArray[(int) e] -= e;
            synchronized (new Test5()) { }
        }
        for (int f = 0; f < 10000; ++f) ;
    }

    static int run5() {
        new Test5();
        for (int i = 0; i < 1000; ++i) {
            test5();
        }
        if (Test5.staticArray[8] != -8000) {
            System.out.println("test5: wrong result: " + Test5.staticArray[8] + " vs expected: -8000");
            return 1;
        }
        return 0;
    }

    // From JDK-8336293
    static class Test6 {
        static long c;
        static int a = 400;
        double[] b = new double[400];
    }

    static void test6() {
        long d;
        double[] e = new double[Test6.a];
        for (int f = 0; f < e.length; f++)
            e[f] = 1.116242;
        d = 1;
        while (++d < 7)
            synchronized (new Test6()) { }
        long g = 0;
        for (int f = 0; f < e.length; f++)
            g += e[f];
        Test6.c += g;
    }

    static int run6() {
        new Test6();
        for (int f = 0; f < 10000; ++f) {
            test6();
        }
        if (Test6.c != 4000000) {
            System.out.println("test6: wrong result: " + Test6.c + " vs expected: 4000000 ");
            return 1;
        }
        return 0;
    }

    // From JDK-8327868
    static class Test7 {
        static int a = 400;
        int[] b = new int[400];
        static int[] staticArray = new int[a];
    }

    static int test7() {
        int l, d = 3;
        for (l = 2; 58 > l; l++) {
            for (int e = 2; e < 8; e += 2)
                for (int f = 1; f < e; f += 2)
                    synchronized (new Test7()) {
                    }
            do
                ; while (d < 2);
            int g = 0;
            do
                g++;
            while (g < 20000);
            Test7.staticArray[1] -= 3023399;
        }
        int h = 0;
        for (int i = 0; i < Test7.staticArray.length; i++)
            h += Test7.staticArray[i];
        return h;
    }

    static int run7() {
        new Test7();
        int res = test7();
        if (res != -169310344) {
            System.out.println("test7: wrong result: " + res + " vs expected: -169310344");
            return 1;
        }
        return 0;
    }

    // from JDK-8329984
    static class Test8 {
        static int a = 400;
        int[] e = new int[400];
    }

    static int test8() {
        int i = 22738;
        int b;
        int h;
        int[] c = new int[Test8.a];
        for (b = 3; b < 273; b++) {
            h = 1;
            while (++h < 97) switch (b % 6 + 56) {
                case 56:
                    c[1] = i;
                case 57:
                    synchronized (new Test8()) {}
            }
        }
        int k = 0;
        for (int j = 0; j < c.length; j++) k += c[j];
        return k;
    }

    public static int run8() {
        new Test8();
        for (int i = 0; i < 20; i++) {
            int res = test8();
            if (res != 22738) {
                System.out.println("test8: wrong result: " + res + " vs expected: 22738");
                return 1;
            }
        }
        return 0;
    }

    // from JDK-8341009
   static class Test9 {
        static int a = 256;
        float[] b = new float[256];
        static long c;
    }

  static void test9() {
    for (int f = 0; f < 10000; ++f) ;
    float[][] g = new float[Test9.a][Test9.a];
    for (int d = 7; d < 16; d++) {
      long e = 1;
      do {
        g[d][(int) e] = d;
        synchronized (new Test9()) {
        }
      } while (++e < 5);
    }
    for (int i = 0; i < Test9.a; ++i) {
      for (int j = 0; j < Test9.a ; ++j) {
          Test9.c += g[i][j];
      }
    }
  }

  static int run9() {
    for (int j = 6; 116 > j; ++j) {
        test9();
    }
    if (Test9.c != 43560) {
        System.out.println("test9: wrong result: " + Test9.c + " vs expected: 43560");
        return 1;
    }
    return 0;
  }
}
