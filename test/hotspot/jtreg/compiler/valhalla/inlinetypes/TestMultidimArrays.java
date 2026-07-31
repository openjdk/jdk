/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @summary Test correct handling of multidimensional arrays.
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestMultidimArrays
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestMultidimArrays::test*
 *                   compiler.valhalla.inlinetypes.TestMultidimArrays
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:MultiArrayExpandLimit=0
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestMultidimArrays::test*
 *                   compiler.valhalla.inlinetypes.TestMultidimArrays
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+StressReflectiveCode
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestMultidimArrays::test*
 *                   compiler.valhalla.inlinetypes.TestMultidimArrays
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:-DoEscapeAnalysis
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestMultidimArrays::test*
 *                   compiler.valhalla.inlinetypes.TestMultidimArrays
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

public class TestMultidimArrays {

    static value class MyValue {
        int val;

        public MyValue(int val) {
            this.val = val;
        }
    }

    static MyValue[][] test1() {
        MyValue[][] arr = new MyValue[2][2];
        arr[0][1] = new MyValue(42);
        for (int i = 0; i < 50_000; i++) {
        }
        return arr;
    }

    static MyValue[][] test2() {
        MyValue[][] arr = new MyValue[2][2];
        arr[0][1] = new MyValue(42);
        return arr;
    }

    static int[][] test3() {
        int[][] arr = new int[2][2];
        arr[0][1] = 42;
        return arr;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; ++i) {
            MyValue[][] res1 = test1();
            Asserts.assertEQ(res1[0][0], null);
            Asserts.assertEQ(res1[0][1], new MyValue(42));

            MyValue[][] res2 = test2();
            Asserts.assertEQ(res2[0][0], null);
            Asserts.assertEQ(res2[0][1], new MyValue(42));

            int[][] res3 = test3();
            Asserts.assertEQ(res3[0][0], 0);
            Asserts.assertEQ(res3[0][1], 42);
        }
    }
}
