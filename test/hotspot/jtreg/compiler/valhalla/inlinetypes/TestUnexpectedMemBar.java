/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=AII-fixed-seed
 * @bug 8270995
 * @summary Membars of non-escaping value class buffer allocations should be removed.
 * @library /test/lib /
 * @enablePreview
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-TieredCompilation -XX:-ReduceInitialCardMarks
 *                   -XX:+AlwaysIncrementalInline -Xbatch -XX:CompileCommand=compileonly,*TestUnexpectedMemBar::test*
 *                   -XX:+StressIGVN -XX:+StressGCM -XX:+StressLCM -XX:StressSeed=851121348
 *                   compiler.valhalla.inlinetypes.TestUnexpectedMemBar
 */

/*
 * @test id=AII
 * @bug 8270995
 * @summary Membars of non-escaping value class buffer allocations should be removed.
 * @library /test/lib /
 * @enablePreview
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-TieredCompilation -XX:-ReduceInitialCardMarks -XX:+AlwaysIncrementalInline
 *                   -Xbatch -XX:CompileCommand=compileonly,*TestUnexpectedMemBar::test*
 *                   -XX:+StressIGVN -XX:+StressGCM -XX:+StressLCM
 *                   compiler.valhalla.inlinetypes.TestUnexpectedMemBar
 */

/*
 * @test id=default
 * @bug 8270995
 * @summary Membars of non-escaping value class buffer allocations should be removed.
 * @library /test/lib /
 * @enablePreview
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -Xbatch -XX:CompileCommand=compileonly,*TestUnexpectedMemBar::test*
 *                   -XX:+StressIGVN -XX:+StressGCM -XX:+StressLCM
 *                   compiler.valhalla.inlinetypes.TestUnexpectedMemBar
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

value class MyValue1UnexpectedMemBar {
    int a = 0;
    int b = 0;
    int c = 0;
    int d = 0;
    int e = 0;

    Integer i;
    int[] array;

    public MyValue1UnexpectedMemBar(Integer i, int[] array) {
        this.i = i;
        this.array = array;
    }
}

value class MyValue2UnexpectedMemBar {
    int a = 0;
    int b = 0;
    int c = 0;
    int d = 0;
    int e = 0;

    NonValueClass obj;
    int[] array;

    public MyValue2UnexpectedMemBar(NonValueClass obj, int[] array) {
        this.obj = obj;
        this.array = array;
    }
}

public class TestUnexpectedMemBar {

    public static int test1(Integer i) {
        int[] array = new int[1];
        MyValue1UnexpectedMemBar vt = new MyValue1UnexpectedMemBar(i, array);
        vt = new MyValue1UnexpectedMemBar(vt.i, vt.array);
        return vt.i + vt.array[0];
    }

    public static int test2(Integer i) {
        int[] array = {i};
        MyValue1UnexpectedMemBar vt = new MyValue1UnexpectedMemBar(i, array);
        vt = new MyValue1UnexpectedMemBar(vt.i, vt.array);
        return vt.i + vt.array[0];
    }

    public static int test3(NonValueClass obj) {
        int[] array = new int[1];
        MyValue2UnexpectedMemBar vt = new MyValue2UnexpectedMemBar(obj, array);
        vt = new MyValue2UnexpectedMemBar(vt.obj, vt.array);
        return vt.obj.x + vt.array[0];
    }

    public static int test4(NonValueClass obj) {
        int[] array = {obj.x};
        MyValue2UnexpectedMemBar vt = new MyValue2UnexpectedMemBar(obj, array);
        vt = new MyValue2UnexpectedMemBar(vt.obj, vt.array);
        return vt.obj.x + vt.array[0];
    }

    public static void main(String[] args) {
        for (int i = 0; i < 100_000; ++i) {
            int res = test1(i);
            Asserts.assertEquals(res, i, "test1 failed");
            res = test2(i);
            Asserts.assertEquals(res, 2*i, "test2 failed");
            res = test3(new NonValueClass(i));
            Asserts.assertEquals(res, i, "test3 failed");
            res = test4(new NonValueClass(i));
            Asserts.assertEquals(res, 2*i, "test4 failed");
        }
    }
}
