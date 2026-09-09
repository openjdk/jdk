/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8389354
 * @summary Test that moving a flat array check does not create an anti-dependence cycle
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:-UseArrayLoadStoreProfile
 *                   -XX:+StressGCM -XX:StressSeed=0
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

public class TestFlatArrayCheckHoisting {
    // A and C arrays are flat, B is too large to flatten and I has identity
    static value class A { final int x; A(int x) { this.x = x; } }
    static value class B { final long x, y; B(long x, long y) { this.x = x; this.y = y; } }
    static value class C { final int x; C(int x) { this.x = x; } }
    static final class I { final int x; I(int x) { this.x = x; } }

    static long helper(Object v) {
        if (v instanceof A x) return x.x;
        if (v instanceof B x) return x.x * 3 + x.y;
        if (v instanceof C x) return x.x * 3;
        return ((I)v).x * 3;
    }

    static long test(Object[] a, int kind) {
        long sum = 0;
        for (int i = 0; i < 150_000; i++) {
            Object v = switch (kind) {
                case 0 -> new A(i);
                case 1 -> new B(i, i ^ 1);
                case 2 -> new I(i);
                default -> new C(i);
            };
            int index = i & 1;
            // The FlatArrayCheck for below access is incorrectly moved out of the loop
            a[index] = v;
            Object loaded = a[index];
            if (helper(loaded) != helper(v)) {
                throw new AssertionError();
            }
            if (kind == 3) {
                C copy = new C(((C)v).x);
                if (v != copy) {
                    throw new AssertionError();
                }
            }
            sum += helper(a[index]);
        }
        return sum;
    }

    public static void main(String[] args) {
        // Alternate non-flat and flat layouts until C2 uses a generic flat-array check
        test(new I[2], 2);
        test(new A[2], 0);
        test(new B[2], 1);
        test(new C[2], 3);
    }
}

