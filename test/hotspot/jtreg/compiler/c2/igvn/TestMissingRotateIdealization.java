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

package compiler.c2.igvn;

/*
 * @test
 * @bug 8396021
 * @summary AddNode::IdealIL and OrI/OrLNode::Ideal turn shift/add and shift/or
 *          patterns into rotates. When an input of the shifts, or of a
 *          (0 - s) shift count, changes, the Add/Or must be put back on the
 *          IGVN worklist. Use -XX:VerifyIterativeGVN=1110 to catch missing
 *          optimizations.
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,${test.main.class}::test*
 *      -XX:VerifyIterativeGVN=1110
 *      ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestMissingRotateIdealization {
    public static void main(String[] args) {
        check(testAddI(42), Integer.rotateRight(42, 16));
        check(testAddL(42), Long.rotateRight(42, 16));
        check(testOrI(42, 5), Integer.rotateRight(42, 5));
        check(testOrL(42, 5), Long.rotateRight(42, 5));
        check(testOrIRotateLeft(42, 5), Integer.rotateLeft(42, 5));
        check(testOrLRotateLeft(42, 5), Long.rotateLeft(42, 5));
    }

    static void check(long actual, long expected) {
        if (actual != expected) {
            throw new RuntimeException("expected " + expected + ", got " + actual);
        }
    }

    // delayedOne is only known to be 1 after loop opts, so the shifted value
    // becomes x late: n -> shift -> add
    static int testAddI(int x) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x >>> 16) + ((x * delayedOne) << 16);
    }

    static long testAddL(long x) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x >>> 16) + ((x * delayedOne) << 48);
    }

    // the count of one shift becomes (0 - s) late: n -> SubI -> shift -> or
    static int testOrI(int x, int s) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x >>> s) | (x << -(s * delayedOne));
    }

    static long testOrL(long x, int s) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x >>> s) | (x << -(s * delayedOne));
    }

    static int testOrIRotateLeft(int x, int s) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x << s) | (x >>> -(s * delayedOne));
    }

    static long testOrLRotateLeft(long x, int s) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x << s) | (x >>> -(s * delayedOne));
    }
}
