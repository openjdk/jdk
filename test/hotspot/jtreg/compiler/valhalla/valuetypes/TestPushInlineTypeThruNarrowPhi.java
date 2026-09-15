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
 * @modules java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:-BackgroundCompilation
 *                   -XX:CompileCommand=option,${test.main.class}::lateInlined1,DelayInline
 *                   -XX:CompileCommand=option,${test.main.class}::lateInlined2,DelayInline
 *                   -XX:CompileCommand=option,${test.main.class}::lateInlined3,DelayInline ${test.main.class}
 * @run main/othervm -XX:-BackgroundCompilation
 *                   -XX:CompileCommand=option,${test.main.class}::lateInlined1,DelayInline
 *                   -XX:CompileCommand=option,${test.main.class}::lateInlined2,DelayInline
 *                   -XX:CompileCommand=option,${test.main.class}::lateInlined3,DelayInline
 *                   -XX:+UnlockDiagnosticVMOptions -XX:-UseFieldFlattening ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import jdk.internal.vm.annotation.NullRestricted;

public class TestPushInlineTypeThruNarrowPhi {
    static value class MyValue1 {
        int x;

        MyValue1(int x) {
            this.x = x;
        }
    }

    static value class MyValue2 {
        long x;

        MyValue2(long x) {
            this.x = x;
        }
    }

    static value class MyValue3 {
        @NullRestricted
        MyValue2 v;

        MyValue3(MyValue2 v) {
            this.v = v;
        }
    }

    static MyValue1 fieldV1 = new MyValue1(42);
    static MyValue2 fieldV2 = new MyValue2(42);
    @NullRestricted
    static MyValue3 fieldV3 = new MyValue3(fieldV2);
    @NullRestricted
    static MyValue3 fieldV4 = new MyValue3(fieldV2);

    static int field;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(true);
            test1(false);
            test2(true, fieldV3);
            test2(false, fieldV3);
            inlined1(true, 0, fieldV3);
            inlined1(true, 42, fieldV3);
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

    static MyValue3 test2(boolean flag1, MyValue3 v3) {
        int flag2 = 42;
        MyValue3 v = new MyValue3(new MyValue2(42));
        for (int i = 1; i < 4; i *= 2) {
            v = inlined1(flag1, flag2, v3);
            flag2 = lateInlined3();
        }
        return v;
    }

    static MyValue3 inlined1(boolean flag1, int flag2, MyValue3 v3) {
        MyValue3 v;
        lateInlined2(flag2, v3, flag1);
        if (flag1) {
            v = fieldV3;
        } else {
            v = fieldV4;
        }
        return v;
    }

    static void lateInlined2(int flag2, MyValue3 v3, boolean flag3) {
        MyValue3 v;
        if (flag2 == 42) {
            if (flag3) {
                v = new MyValue3(new MyValue2(42));
            } else {
                v = new MyValue3(new MyValue2(42));
            }
            field = 42;
        } else {
            v = fieldV4;
        }
        fieldV3 = v;
        fieldV4 = new MyValue3(new MyValue2(42));
    }

    static int lateInlined3() {
        return 42;
    }
}
