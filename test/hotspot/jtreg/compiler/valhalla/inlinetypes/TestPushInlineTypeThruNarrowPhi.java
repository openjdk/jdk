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
 * @bug 8389623
 * @enablePreview
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:+AlwaysIncrementalInline ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

public class TestPushInlineTypeThruNarrowPhi {
    static value class MyValue1 {
        int x;

        MyValue1(int x) {
            this.x = x;
        }
    }

    static MyValue1 fieldV1 = new MyValue1(42);
    static MyValue1 fieldV2 = new MyValue1(42);
    static Object fieldO1 = new MyValue1(42);
    static Object fieldO2 = new Object();
    static volatile int volatileField;
    static volatile int field;
    
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(true);
            test1(false);
            test2(true, true, fieldO1, fieldV1);
            test2(true, false, fieldO1, fieldV1);
            test2(false, true, fieldO1, fieldV1);
            test2(false, false, fieldO1, fieldV1);
            lateInlined2(0, fieldO1, fieldV1);
        }
    }

    static MyValue1 test1(boolean flag1) {
        MyValue1 res = null;
        if (flag1) {
            lateInlined1();
            res = fieldV1;
        } else {
            lateInlined1();
            res = fieldV1;
        }
        return res;
    }

    static void lateInlined1() {
        fieldV1 = new MyValue1(42);
    }

    static Object test2(boolean flag1, boolean flag2, Object obj, MyValue1 val) {
        int i;
        for (i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
            }
        }
        Object res = null;
        if (flag2) {
            if (flag1) {
                lateInlined2(i, obj, val);
                res = fieldO1;
            } else {
                lateInlined2(i, obj, val);
                res = fieldO1;
            }
            field = 42;
        } else {
            if (flag1) {
                lateInlined2(i, obj, val);
                res = fieldO1;
            } else {
                lateInlined2(i, obj, val);
                res = fieldO1;
            }
            field = 42;
        }
        return res;
    }

    static void lateInlined2(int i, Object obj, MyValue1 val) {
        volatileField = 42;
        Object o = null;

        if (i == 10) {
            o = new MyValue1(42);
        } else {
            o = obj;
        }
        fieldO1 = o;
    }
}
