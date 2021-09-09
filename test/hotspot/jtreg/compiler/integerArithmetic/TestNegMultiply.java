/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
 * @bug 8273454
 * @summary Test transformation (-a)*(-b) = a*b
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement TestNegMultiply
 *
 */

import java.util.Random;

public class TestNegMultiply {
    private static Random random = new Random();
    private static final int TEST_COUNT = 2000;

    private static int test(int a, int b) {
        return (-a) * (-b);
    }
    private static void testInt(int a, int b) {
        int expected = (-a) * (-b);
        for (int i = 0; i < 20_000; i++) {
            if (expected != test(a, b)) {
                throw new RuntimeException("Incorrect result.");
            }
        }
    }

    private static long test(long a, long b) {
        return (-a) * (-b);
    }

    private static void testLong(long a, long b) {
        long expected = (-a) * (-b);
        for (int i = 0; i < 20_000; i++) {
            if (expected != test(a, b)) {
                throw new RuntimeException("Incorrect result.");
            }
        }
    }

    private static float test(float a, float b) {
        return (-a) * (-b);
    }

    private static void testFloat(float a, float b) {
        float expected = (-a) * (-b);
        for (int i = 0; i < 20_000; i++) {
            if (expected != test(a, b)) {
                throw new RuntimeException("Incorrect result.");
            }
        }
    }

    private static double test(double a, double b) {
        return (-a) * (-b);
    }

    private static void testDouble(double a, double b) {
        double expected = (-a) * (-b);
        for (int i = 0; i < 20_000; i++) {
            if (expected != test(a, b)) {
                throw new RuntimeException("Incorrect result.");
            }
        }
    }

    private static void runIntTests() {
        for (int index = 0; index < TEST_COUNT; index ++) {
            int a = random.nextInt();
            int b = random.nextInt();
            testInt(a, b);
        }
    }

    private static void runLongTests() {
        for (int index = 0; index < TEST_COUNT; index ++) {
            long a = random.nextLong();
            long b = random.nextLong();
            testLong(a, b);
        }
    }

    private static void runFloatTests() {
        for (int index = 0; index < TEST_COUNT; index ++) {
            float a = random.nextFloat();
            float b = random.nextFloat();
            testFloat(a, b);
        }
    }

    private static void runDoubleTests() {
        for (int index = 0; index < TEST_COUNT; index ++) {
            double a = random.nextDouble();
            double b = random.nextDouble();
            testDouble(a, b);
        }
    }

    public static void main(String[] args) {
        runIntTests();
        runLongTests();
        runFloatTests();
        runDoubleTests();
    }
}
