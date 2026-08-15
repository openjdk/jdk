/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @bug 8377480
 * @summary [lworld] incorrect execution due to EA pointer comparison optimization at scalarized call
 * @enablePreview
 * @run main/othervm -XX:-BackgroundCompilation -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestEAScalarizedArg::notInlined*  ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

public class TestEAScalarizedArg {
    static value class MyValue {
        Object o;

        MyValue(Object o) {
            this.o = o;
        }
    }

    static int test1(Object o) {
        Object o2 = new Object();
        MyValue v = new MyValue(null);
        Object res = notInlined(v, o);
        if (res == null) {
            return 1;
        }
        return 2;
    }

    static Object notInlined(MyValue arg1, Object arg2) {
        return arg2;
    }

    static int test2() {
        Object o2 = new Object();
        MyValue v = new MyValue(null);
        Object res = notInlined2(v);
        if (res == null) {
            return 1;
        }
        return 2;
    }

    static Object notInlined2(MyValue arg1) {
        return arg1;
    }

    static public void main(String[] args) {
        Object o = new Object();
        MyValue v = new MyValue(o);
        for (int i = 0; i < 20_000; i++ ) {
            test1(o);
            test1(null);
            test2();
        }
        if (test1(o) != 2) {
            throw new RuntimeException("execution failed");
        }
        if (test2() != 2) {
            throw new RuntimeException("execution failed");
        }
    }

}
