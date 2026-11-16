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

import jdk.internal.value.ValueClass;

/*
 * @test
 * @bug 8395724
 * @summary [valhalla] "adr_type for memory phis only" assert failure
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -XX:CompileCommand=dontinline,${test.main.class}::notInlined -XX:-BackgroundCompilation ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestFlatArrayCheckCloneLoop {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(0, false, false);
            earlyReturn1 = false;
            earlyReturn2 = false;
            array = notFlatArray1;
            test1(999, true, true);
            inlined(999, true, false);
            earlyReturn1 = true;
            earlyReturn2 = false;
            array = notFlatArray1;
            inlined(999, false, true);
            inlined(999, true, true);
            array = notFlatArray2;
            inlined(999, false, true);
            inlined(999, true, true);
            earlyReturn1 = false;
            earlyReturn2 = true;
            array = notFlatArray1;
            inlined(999, false, true);
            inlined(999, true, true);
            array = notFlatArray2;
            inlined(999, false, true);
            inlined(999, true, true);
        }
    }

    static class A {
    }

    static value class B {
        long field1;
        B(long field1, long field2) {
            this.field1 = field1;
        }
    }

    static Object[] notFlatArray1 = new A[1000];
    static Object[] notFlatArray2 = ValueClass.newNullRestrictedAtomicArray(B.class, 1000, new B(42, 42));
    static Object[] array;
    static Object field;
    static int intField;
    static boolean earlyReturn1;
    static boolean earlyReturn2;
    static Boolean alwaysFalse = false;

    static void test1(int len, boolean flag1, boolean flag2) {
        if (flag2) {
            inlined(len, flag1, flag2);
        }
    }

    static void inlined(int len, boolean flag1, boolean flag2) {
        boolean flag = flag1;
        for (int i = 0; i < len; i++) {
            if (flag) {
                notInlined();
            }
            Object[] localArray1 = array;
            if (localArray1 == null) {
                throw new RuntimeException("unreachable");
            }
            if (earlyReturn2) {
                intField = ValueClass.isFlatArray(localArray1) ? 0 : 1;
                return;
            }

            if (earlyReturn1) {
                field = localArray1[i+1];
                return;
            }
            if (flag2) {
                flag = flag1;
            } else {
                flag = alwaysFalse;
            }
        }
    }

    static void notInlined() {
    }
}
