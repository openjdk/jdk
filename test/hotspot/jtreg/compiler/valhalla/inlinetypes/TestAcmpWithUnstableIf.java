/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @summary Test that deoptimization at unstable ifs in acmp works as expected.
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+StressUnstableIfTraps compiler.valhalla.inlinetypes.TestAcmpWithUnstableIf
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestAcmpWithUnstableIf::test* -Xbatch
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressUnstableIfTraps compiler.valhalla.inlinetypes.TestAcmpWithUnstableIf
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class TestAcmpWithUnstableIf {

    public static final int     EQUAL = Utils.getRandomInstance().nextInt();
    public static final int NOT_EQUAL = Utils.getRandomInstance().nextInt();

    static value class MyValue {
        int x;

        public MyValue(int x) {
            this.x = x;
        }
    }

    public static int test1(MyValue val1, MyValue val2, int resEqual, int resNotEqual) {
        if (val1 == val2) {
            return resEqual;
        }
        return resNotEqual;
    }

    public static int test2(MyValue val1, MyValue val2, int resEqual, int resNotEqual) {
        if (val1 == val2) {
            return resEqual;
        }
        return resNotEqual;
    }

    public static int test3(MyValue val1, MyValue val2, int resEqual, int resNotEqual) {
        if (val1 == val2) {
            return resEqual;
        }
        return resNotEqual;
    }

    public static int test4(MyValue val1, MyValue val2, int resEqual, int resNotEqual) {
        if (val1 == val2) {
            return resEqual;
        }
        return resNotEqual;
    }

    public static int test5(MyValue val1, MyValue val2, int resEqual, int resNotEqual) {
        if (val1 != val2) {
            return resNotEqual;
        }
        return resEqual;
    }

    public static int test6(MyValue val1, MyValue val2, int resEqual, int resNotEqual) {
        if (val1 != val2) {
            return resNotEqual;
        }
        return resEqual;
    }

    public static int test7(MyValue val1, MyValue val2, int resEqual, int resNotEqual) {
        if (val1 != val2) {
            return resNotEqual;
        }
        return resEqual;
    }

    public static int test8(MyValue val1, MyValue val2, int resEqual, int resNotEqual) {
        if (val1 != val2) {
            return resNotEqual;
        }
        return resEqual;
    }

    public static void main(String[] args) {
        MyValue val = new MyValue(EQUAL);
        MyValue val_copy = new MyValue(EQUAL);
        MyValue val_diff = new MyValue(EQUAL + 1);

        // Warmup
        for (int i = 0; i < 50_000; ++i) {
            // Equal arguments, same oop
            Asserts.assertEquals(test1(val, val, EQUAL, NOT_EQUAL), EQUAL);
            Asserts.assertEquals(test5(val, val, EQUAL, NOT_EQUAL), EQUAL);

            // Equal arguments, different oop
            Asserts.assertEquals(test2(val, val_copy, EQUAL, NOT_EQUAL), EQUAL);
            Asserts.assertEquals(test6(val, val_copy, EQUAL, NOT_EQUAL), EQUAL);

            // Different arguments
            Asserts.assertEquals(test3(val, val_diff, EQUAL, NOT_EQUAL), NOT_EQUAL);
            Asserts.assertEquals(test4(val, val_diff, EQUAL, NOT_EQUAL), NOT_EQUAL);

            Asserts.assertEquals(test7(val, val_diff, EQUAL, NOT_EQUAL), NOT_EQUAL);
            Asserts.assertEquals(test8(val, val_diff, EQUAL, NOT_EQUAL), NOT_EQUAL);
        }

        // Now trigger deoptimization

        // Different arguments
        Asserts.assertEquals(test1(val, val_diff, EQUAL, NOT_EQUAL), NOT_EQUAL);
        Asserts.assertEquals(test2(val, val_diff, EQUAL, NOT_EQUAL), NOT_EQUAL);

        Asserts.assertEquals(test5(val, val_diff, EQUAL, NOT_EQUAL), NOT_EQUAL);
        Asserts.assertEquals(test6(val, val_diff, EQUAL, NOT_EQUAL), NOT_EQUAL);

        // Equal arguments, same oop
        Asserts.assertEquals(test3(val, val, EQUAL, NOT_EQUAL), EQUAL);
        Asserts.assertEquals(test7(val, val, EQUAL, NOT_EQUAL), EQUAL);

        // Equal arguments, different oop
        Asserts.assertEquals(test4(val, val_copy, EQUAL, NOT_EQUAL), EQUAL);
        Asserts.assertEquals(test8(val, val_copy, EQUAL, NOT_EQUAL), EQUAL);
    }
}
