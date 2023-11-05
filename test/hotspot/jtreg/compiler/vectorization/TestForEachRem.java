/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8251994
 * @summary Test vectorization of Streams$RangeIntSpliterator::forEachRemaining
 * @requires vm.compiler2.enabled & vm.compMode != "Xint"
 *
 * @run main/othervm -Xbatch compiler.vectorization.TestForEachRem test1
 * @run main/othervm -Xbatch compiler.vectorization.TestForEachRem test2
 * @run main/othervm -Xbatch compiler.vectorization.TestForEachRem test3
 * @run main/othervm -Xbatch compiler.vectorization.TestForEachRem test4
 * @run main/othervm -Xbatch compiler.vectorization.TestForEachRem test5
 * @run main/othervm -Xbatch compiler.vectorization.TestForEachRem test6
 */

package compiler.vectorization;

import java.util.stream.IntStream;

public class TestForEachRem {
    static final int RANGE = 512;
    static final int ITER  = 100;

    static void test1(int[] data) {
       IntStream.range(0, RANGE).parallel().forEach(j -> {
           data[j] = j + 1;
       });
    }

    static void test2(int[] data) {
       IntStream.range(0, RANGE - 1).forEach(j -> {
           data[j] = data[j] + data[j + 1];
       });
    }

    static void test3(int[] data, int A, int B) {
       IntStream.range(0, RANGE - 1).forEach(j -> {
           data[j] = A * data[j] + B * data[j + 1];
       });
    }

    static void test4(int[] data) {
       IntStream.range(0, RANGE - 1).forEach(j -> {
           data[j + 1] = data[j];
       });
    }

    static void test5(int[] data) {
       IntStream.range(0, RANGE - 2).forEach(j -> {
           data[j + 2] = data[j];
       });
    }

    static void initByte(byte[] data) {
       IntStream.range(0, RANGE).parallel().forEach(j -> {
           data[j] = (byte)j;
       });
    }

    static void test6(byte[] data) {
       // 2-byte offset -> can only vectorize if alignment not required by hardware
       IntStream.range(0, RANGE - 2).forEach(j -> {
           data[j] = data[j + 2];
       });
    }

    static void verify(String name, int[] data, int[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    static void verify(String name, byte[] data, byte[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    public static void main(String[] args) {
        int[] data = new int[RANGE];
        int[] gold = new int[RANGE];
        byte[] dataB = new byte[RANGE];
        byte[] goldB = new byte[RANGE];

        if (args.length == 0) {
            throw new RuntimeException(" Missing test name: test1, test2, test3, test4, test5");
        }

        if (args[0].equals("test1")) {
            System.out.println(" Run test1 ...");
            test1(gold);
            for (int i = 0; i < ITER; i++) {
                test1(data);
            }
            verify("test1", data, gold);
            System.out.println(" Finished test1.");
        }

        if (args[0].equals("test2")) {
            System.out.println(" Run test2 ...");
            test1(gold);
            test2(gold);
            for (int i = 0; i < ITER; i++) {
                test1(data); // reset
                test2(data);
            }
            verify("test2", data, gold);
            System.out.println(" Finished test2.");
        }

        if (args[0].equals("test3")) {
            System.out.println(" Run test3 ...");
            test1(gold);
            test3(gold, 2, 3);
            for (int i = 0; i < ITER; i++) {
                test1(data); // reset
                test3(data, 2, 3);
            }
            verify("test3", data, gold);
            System.out.println(" Finished test3.");
        }

        if (args[0].equals("test4")) {
            System.out.println(" Run test4 ...");
            test1(gold); // reset
            test4(gold);
            for (int i = 0; i < ITER; i++) {
                test1(data); // reset
                test4(data);
            }
            verify("test4", data, gold);
            System.out.println(" Finished test4.");
        }

        if (args[0].equals("test5")) {
            System.out.println(" Run test5 ...");
            test1(gold); // reset
            test5(gold);
            for (int i = 0; i < ITER; i++) {
                test1(data); // reset
                test5(data);
            }
            verify("test5", data, gold);
            System.out.println(" Finished test5.");
        }

        if (args[0].equals("test6")) {
            System.out.println(" Run test6 ...");
            initByte(goldB); // reset
            test6(goldB);
            for (int i = 0; i < ITER; i++) {
                initByte(dataB); // reset
                test6(dataB);
            }
            verify("test6", dataB, goldB);
            System.out.println(" Finished test6.");
        }

    }
}
