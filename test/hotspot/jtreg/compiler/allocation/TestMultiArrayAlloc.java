/*
 * Copyright (c) 2026, BELLSOFT. All rights reserved.
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
 * @test id=shenandoah
 * @bug 8308105
 * @summary Test correctness of inlined 2D array allocation
 * @requires vm.gc.Shenandoah
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+UseShenandoahGC compiler.allocation.TestMultiArrayAlloc
 */

/*
 * @test id=default
 * @bug 8308105
 * @summary Test correctness of inlined 2D array allocation
 * @run main/othervm -Xcomp -XX:-TieredCompilation compiler.allocation.TestMultiArrayAlloc
 */

package compiler.allocation;

public class TestMultiArrayAlloc {

    static int[][]    allocInt(int n1, int n2) { return new int[n1][n2]; }
    static Object[][] allocObj(int n1, int n2) { return new Object[n1][n2]; }
    static String[][] allocStr(int n1, int n2) { return new String[n1][n2]; }

    static void check(boolean cond, String msg) { if (!cond) throw new RuntimeException(msg); }

    static void testArrayContents() {
        int n1 = 10, n2 = 20;
        int[][]    ints = allocInt(n1, n2);
        Object[][] objs = allocObj(n1, n2);
        String[][] strs = allocStr(n1, n2);
        check(ints.length == n1, "int outer length");
        check(objs.length == n1, "Object outer length");
        check(strs.length == n1, "String outer length");
        for (int i = 0; i < n1; i++) {
            check(ints[i] instanceof int[]    && ints[i].length == n2, "int inner at " + i);
            check(objs[i] instanceof Object[] && objs[i].length == n2, "Object inner at " + i);
            check(strs[i] instanceof String[] && strs[i].length == n2, "String inner at " + i);
            for (int j = 0; j < n2; j++) {
                check(ints[i][j] == 0,    "int not zero at ["    + i + "][" + j + "]");
                check(objs[i][j] == null, "Object not null at [" + i + "][" + j + "]");
                check(strs[i][j] == null, "String not null at [" + i + "][" + j + "]");
            }
        }
        // length1 == 0
        int[][] b = allocInt(0, n2);
        check(b.length == 0, "int[0][n2] wrong length");
        // length2 == 0
        int[][] c = allocInt(n1, 0);
        for (int i = 0; i < n1; i++) {
            check(c[i].length == 0, "int[n1][0] inner length wrong at " + i);
        }
    }

    // new int[0][-1] must throw NegativeArraySizeException
    static final int[] DIMS = {-10, -1, 0, 1, 10};

    static void testExceptions() {
        for (int n1 : DIMS) {
            for (int n2 : DIMS) {
                boolean expectThrow = (n1 < 0) || (n2 < 0);
                checkThrows(() -> allocInt(n1, n2), expectThrow, "allocInt(" + n1 + ", " + n2 + ")");
                checkThrows(() -> allocObj(n1, n2), expectThrow, "allocObj(" + n1 + ", " + n2 + ")");
                checkThrows(() -> allocStr(n1, n2), expectThrow, "allocStr(" + n1 + ", " + n2 + ")");
            }
        }
    }

    static void checkThrows(Runnable action, boolean expectThrow, String msg) {
        boolean threw = false;
        try {
            action.run();
        } catch (NegativeArraySizeException e) {
            threw = true;
        }
        check(threw == expectThrow,
              msg + ": expected NegativeArraySizeException=" + expectThrow + " but threw=" + threw);
    }

    static void testLargeOuterDimension() {
        int n1 = 100_000, n2 = 2;
        int[][] ints = allocInt(n1, n2);
        check(ints.length == n1, "large outer: length");
        for (int i = 0; i < n1; i++) {
            check(ints[i] != null && ints[i].length == n2, "large outer: inner at " + i);
        }
    }

    static void testRefsAfterGC() {
        int count = 5000, n1 = 4, n2 = 4;
        int[][][] refs = new int[count][][];
        for (int i = 0; i < count; i++) {
            refs[i] = allocInt(n1, n2);
            for (int j = 0; j < n1; j++) {
                refs[i][j][0] = i;
            }
        }
        System.gc();
        for (int i = 0; i < count; i++) {
            check(refs[i] != null, "GC collected outer array at " + i);
            for (int j = 0; j < n1; j++) {
                check(refs[i][j] != null, "GC collected inner array at [" + i + "][" + j + "]");
            }
            check(refs[i][0][0] == i, "GC corrupted value at " + i);
        }
    }

    public static void main(String[] args) {
        testArrayContents();
        testExceptions();
        testLargeOuterDimension();
        testRefsAfterGC();
    }
}
