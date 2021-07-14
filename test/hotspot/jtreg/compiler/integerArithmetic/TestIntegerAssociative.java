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
 * @bug 8270366
 * @summary Test corner cases of integer associative rules
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement TestIntegerAssociative
 *
 */

public class TestIntegerAssociative {
    public static void main(String[] args) {
        int a = 17;
        int b = 34;
        int c = 10;

        // Test a*b+a*c => a*(b+c) transformation
        runAddTest(a, b, c);

        a = Integer.MAX_VALUE - 4;
        runAddTest(a, b, c);

        a = 7;
        b = Integer.MAX_VALUE;
        runAddTest(a, b, c);

        c = Integer.MAX_VALUE;
        runAddTest(a, b, c);

        b = Integer.MIN_VALUE + 7;
        c = Integer.MIN_VALUE + 18;
        runAddTest(a, b, c);

        a = Integer.MAX_VALUE;
        b = Integer.MAX_VALUE;
        c = Integer.MAX_VALUE;
        runAddTest(a, b, c);

        // Test a*b-a*c => a*(b-c) transformation
        a = 17;
        b = 34;
        c = 10;
        runSubTest(a, b, c);

        a = Integer.MIN_VALUE + 40;
        runSubTest(a, b, c);

        a = Integer.MAX_VALUE - 4;
        runSubTest(a, b, c);

        a = 34;
        b = Integer.MIN_VALUE + 3;
        c = Integer.MIN_VALUE + 20;
        runSubTest(a, b, c);

        a = Integer.MAX_VALUE;
        b = Integer.MAX_VALUE;
        c = Integer.MAX_VALUE;
        runSubTest(a, b, c);

        a = Integer.MIN_VALUE;
        b = Integer.MIN_VALUE;
        c = Integer.MIN_VALUE;
        runSubTest(a, b, c);
    }

    private static void runAddTest(int a, int b, int c) {
        int intResult = addInt(a, b, c);
        for (int i = 0; i < 20_000; i++) {
            if (intResult != addComp(a, b, c)) {
                throw new RuntimeException("incorrect result");
            }
        }
    }
    // Method should run under interpreter mode
    private static int addInt(int a, int b, int c) {
        return a * b + a * c;
    }
    // Method should be compiled
    private static int addComp(int a, int b, int c) {
        return a * b + a * c;
    }

    private static void runSubTest(int a, int b, int c) {
        int intResult = subInt(a, b, c);
        for (int i = 0; i < 20_000; i++) {
            if (intResult != subComp(a, b, c)) {
                throw new RuntimeException("incorrect result");
            }
        }
    }
    // Method should run under interpreter mode
    private static int subInt(int a, int b, int c) {
        return a * b + a * c;
    }
    // Method should be compiled
    private static int subComp(int a, int b, int c) {
        return a * b + a * c;
    }
}
