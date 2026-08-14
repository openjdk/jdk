/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8260034 8260225 8260283 8261037 8261874 8262128 8262831 8306986 8355299 8378780
 * @summary A selection of generated tests that triggered bugs not covered by other tests.
 * @enablePreview
 * @library /testlibrary /test/lib /
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch
 *                   compiler.valhalla.inlinetypes.TestGenerated
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:ForceNonTearable=*
 *                   compiler.valhalla.inlinetypes.TestGenerated
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:-UseArrayFlattening
 *                   compiler.valhalla.inlinetypes.TestGenerated
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:+UseNullableAtomicValueFlattening -XX:+UseNullFreeAtomicValueFlattening -XX:+UseNullFreeNonAtomicValueFlattening
 *                   compiler.valhalla.inlinetypes.TestGenerated
 */

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import jdk.test.whitebox.WhiteBox;

@LooselyConsistentValue
value class EmptyPrimitive {

}

value class EmptyValue {

}

@LooselyConsistentValue
value class MyValue1Generated {
    int x = 42;
    int[] array = new int[1];
}

@LooselyConsistentValue
value class MyValue2Generated {
    int[] a = new int[1];
    int[] b = new int[6];
    int[] c = new int[5];
}

@LooselyConsistentValue
value class MyValue3Generated {
    int[] intArray = new int[1];
    float[] floatArray = new float[1];
}

@LooselyConsistentValue
value class MyValue4Generated {
    short b = 2;
    int c = 8;
}

class MyValue4Wrapper {
    public MyValue4Generated val;

    public MyValue4Wrapper(MyValue4Generated val) {
        this.val = val;
    }
}

@LooselyConsistentValue
value class MyValue5Generated {
    int b = 2;
}

value class MyValue6Generated {
    int x = 42;
}

public class TestGenerated {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final boolean SLOW_CONFIGURATION = (WHITE_BOX.getIntxVMFlag("TieredStopAtLevel").intValue() < 4);

    EmptyPrimitive f1 = new EmptyPrimitive();
    EmptyPrimitive f2 = new EmptyPrimitive();

    public TestGenerated() {
        f4 = new MyValue1Generated();
        e = new MyValue4Generated();
        test13_t = new MyValue5Generated();
        super();
    }

    void test1(EmptyPrimitive[] array) {
        for (int i = 0; i < 10; ++i) {
            f1 = array[0];
            f2 = array[0];
        }
    }

    MyValue1Generated test2(MyValue1Generated[] array) {
        MyValue1Generated res = new MyValue1Generated();
        for (int i = 0; i < array.length; ++i) {
            res = array[i];
        }
        for (int i = 0; i < 1000; ++i) {

        }
        return res;
    }

    void test3(MyValue1Generated[] array) {
        for (int i = 0; i < array.length; ++i) {
            array[i] = new MyValue1Generated();
        }
        for (int i = 0; i < 1000; ++i) {

        }
    }

    void test4(MyValue1Generated[] array) {
        array[0].array[0] = 0;
    }

    int test5(MyValue1Generated[] array) {
        return array[0].array[0];
    }

    long f3;
    @NullRestricted
    MyValue1Generated f4;

    void test6() {
        f3 = 123L;
        int res = f4.x;
        if (res != 42) {
            throw new RuntimeException("test6 failed");
        }
    }

    MyValue2Generated f5;

    void test7(boolean b) {
        MyValue2Generated[] array1 = (MyValue2Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2Generated.class, 6, new MyValue2Generated());
        array1[0] = new MyValue2Generated();
        array1[1] = new MyValue2Generated();
        array1[2] = new MyValue2Generated();
        array1[3] = new MyValue2Generated();
        array1[4] = new MyValue2Generated();
        array1[5] = new MyValue2Generated();

        MyValue2Generated h = new MyValue2Generated();
        MyValue2Generated n = new MyValue2Generated();
        int[] array2 = new int[1];

        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 10; ++j) {
                array1[0] = array1[0];
                if (i == 1) {
                    h = h;
                    array2[0] *= 42;
                }
            }
        }
        if (b) {
            f5 = n;
        }
    }

    boolean test8(MyValue1Generated[] array) {
        return array[0].array == array[0].array;
    }

    void test9(boolean b) {
        MyValue1Generated[] array = (MyValue1Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1Generated.class, 1, new MyValue1Generated());
        if (b) {
            for (int i = 0; i < 10; ++i) {
                if (array != array) {
                    array = null;
                }
            }
        }
    }

    int[] f6 = new int[1];

    void test10(MyValue3Generated[] array) {
        float[] floatArray = array[0].floatArray;
        if (f6 == f6) {
            f6 = array[0].intArray;
        }
    }

    void test11(MyValue3Generated[] array) {
        float[] floatArray = array[0].floatArray;
        if (array[0].intArray[0] != 42) {
            throw new RuntimeException("test11 failed");
        }
    }

    MyValue4Generated[] d = (MyValue4Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue4Generated.class, 1, new MyValue4Generated());
    @NullRestricted
    MyValue4Generated e;
    byte f;

    byte test12() {
        MyValue4Generated i = new MyValue4Generated();
        for (int j = 0; j < 6; ++j) {
            MyValue4Generated[] k = (MyValue4Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue4Generated.class, 0, new MyValue4Generated());
            if (i.b < 101) {
                i = e;
            }
            for (int l = 0; l < 9; ++l) {
                MyValue4Generated m = new MyValue4Generated();
                i = m;
            }
        }
        if (d[0].c > 1) {
            for (int n = 0; n < 7; ++n) {
            }
        }
        return f;
    }

    int test13_iField;
    MyValue5Generated test13_c;
    @NullRestricted
    MyValue5Generated test13_t;

    void test13(MyValue5Generated[] array) {
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 10; ++j) {
                test13_iField = 6;
            }
            for (int j = 0; j < 2; ++j) {
                test13_iField += array[0].b;
            }
            MyValue5Generated[] array2 = (MyValue5Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue5Generated.class, 1, new MyValue5Generated());
            test13_c = array[0];
            array2[0] = test13_t;
        }
    }

    void test14(boolean b, MyValue4Generated val) {
        for (int i = 0; i < 10; ++i) {
            if (b) {
                val = new MyValue4Generated();
            }
            MyValue4Generated[] array = (MyValue4Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue4Generated.class, 1, new MyValue4Generated());
            array[0] = val;

            for (int j = 0; j < 5; ++j) {
                for (int k = 0; k < 5; ++k) {
                }
            }
        }
    }

    void test15() {
        MyValue4Generated val = new MyValue4Generated();
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 10; ++j) {
                MyValue4Generated[] array = (MyValue4Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue4Generated.class, 1, new MyValue4Generated());
                for (int k = 0; k < 10; ++k) {
                    array[0] = val;
                    val = array[0];
                }
            }
        }
    }

    void test16() {
        MyValue4Generated val = new MyValue4Generated();
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 10; ++j) {
                val = (new MyValue4Wrapper(val)).val;
                for (int k = 0; k < 10; ++k) {
                }
            }
        }
    }

    static MyValue6Generated test17Field = new MyValue6Generated();

    void test17() {
        for (int i = 0; i < 10; ++i) {
            MyValue6Generated val = new MyValue6Generated();
            for (int j = 0; j < 10; ++j) {
                test17Field = val;
            }
        }
    }

    EmptyValue test18Field;

    EmptyValue test18() {
        EmptyValue val = new EmptyValue();
        test18Field = val;
        return test18Field;
    }

    MyValue1Generated test19Field = new MyValue1Generated();

    public void test19() {
        for (int i = 0; i < 10; ++i) {
            MyValue1Generated val = new MyValue1Generated();
            for (int j = 0; j < 10; ++j) {
                test19Field = val;
            }
        }
    }

    public static void main(String[] args) {
        TestGenerated t = new TestGenerated();
        EmptyPrimitive[] array1 = (EmptyPrimitive[])ValueClass.newNullRestrictedNonAtomicArray(EmptyPrimitive.class, 1, new EmptyPrimitive());
        MyValue1Generated[] array2 = (MyValue1Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1Generated.class, 10, new MyValue1Generated());
        MyValue1Generated[] array3 = (MyValue1Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1Generated.class, 1, new MyValue1Generated());
        array3[0] = new MyValue1Generated();
        MyValue3Generated[] array4 = (MyValue3Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3Generated.class, 1, new MyValue3Generated());
        array4[0] = new MyValue3Generated();
        MyValue5Generated[] array5 = (MyValue5Generated[])ValueClass.newNullRestrictedNonAtomicArray(MyValue5Generated.class, 1, new MyValue5Generated());
        array5[0] = new MyValue5Generated();
        array4[0].intArray[0] = 42;

        int iterations = SLOW_CONFIGURATION ? 1_000 : 50_000;
        for (int i = 0; i < iterations; ++i) {
            t.test1(array1);
            t.test2(array2);
            t.test3(array2);
            t.test4(array3);
            t.test5(array3);
            t.test6();
            t.test7(false);
            t.test8(array3);
            t.test9(true);
            t.test10(array4);
            t.test11(array4);
            t.test12();
            t.test13(array5);
            t.test14(false, new MyValue4Generated());
            t.test15();
            t.test16();
            t.test17();
            t.test18();
            t.test19();
        }
    }
}
