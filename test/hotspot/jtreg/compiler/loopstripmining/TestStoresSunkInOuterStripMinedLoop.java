/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8356708
 * @summary C2: loop strip mining expansion doesn't take sunk stores into account
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:LoopMaxUnroll=0
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=26601954 TestStoresSunkInOuterStripMinedLoop
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:LoopMaxUnroll=0
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM TestStoresSunkInOuterStripMinedLoop
 * @run main TestStoresSunkInOuterStripMinedLoop
 *
 */

public class TestStoresSunkInOuterStripMinedLoop {
    private static int field;
    private static volatile int volatileField;

    public static void main(String[] args) {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        for (int i = 0; i < 20_000; i++) {
            field = 0;
            test1();
            if (field != 1500) {
                throw new RuntimeException(field + " != 1500");
            }
            a1.field = 0;
            test2(a1, a2);
            if (a1.field != 1500) {
                throw new RuntimeException(a1.field + " != 1500");
            }
            a1.field = 0;
            test3(a1, a2);
            if (a1.field != 1500) {
                throw new RuntimeException(a1.field + " != 1500");
            }
            a1.field = 0;
            test4(a1, a2, a3);
            if (a1.field != 1500) {
                throw new RuntimeException(a1.field + " != 1500");
            }
        }
    }

    // Single store sunk in outer loop, no store in inner loop
    private static float test1() {
        int v = field;
        float f = 1;
        for (int i = 0; i < 1500; i++) {
            f *= 2;
            v++;
            field = v;
        }
        return f;
    }

    // Multiple stores sunk in outer loop, no store in inner loop
    private static float test2(A a1, A a2) {
        field = a1.field + a2.field;
        volatileField = 42;
        int v = a1.field;
        float f = 1;
        for (int i = 0; i < 1500; i++) {
            f *= 2;
            v++;
            a1.field = v;
            a2.field = v;
        }
        return f;
    }

    // Store sunk in outer loop, store in inner loop
    private static float test3(A a1, A a2) {
        field = a1.field + a2.field;
        volatileField = 42;
        int v = a1.field;
        float f = 1;
        A a = a2;
        for (int i = 0; i < 1500; i++) {
            f *= 2;
            v++;
            a.field = v;
            a = a1;
            a2.field = v;
        }
        return f;
    }

    // Multiple stores sunk in outer loop, store in inner loop
    private static float test4(A a1, A a2, A a3) {
        field = a1.field + a2.field + a3.field;
        volatileField = 42;
        int v = a1.field;
        float f = 1;
        A a = a2;
        for (int i = 0; i < 1500; i++) {
            f *= 2;
            v++;
            a.field = v;
            a = a1;
            a2.field = v;
            a3.field = v;
        }
        return f;
    }

    static class A {
        int field;
    }
}
