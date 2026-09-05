/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import java.lang.invoke.*;
import java.lang.reflect.Field;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

/*
 * @test id=no-flattening
 * @key randomness
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 * @summary Detect tearing on flat accesses and buffering.
 * @library /testlibrary /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:-UseFieldFlattening -XX:-UseArrayFlattening
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   compiler.valhalla.inlinetypes.TestTearing
 */

/*
 * @test id=no-flattening-AII
 * @key randomness
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 * @summary Detect tearing on flat accesses and buffering.
 * @library /testlibrary /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:-UseFieldFlattening -XX:-UseArrayFlattening
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *                   compiler.valhalla.inlinetypes.TestTearing
 */

/*
 * @test id=no-flattening-di
 * @key randomness
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 * @summary Detect tearing on flat accesses and buffering.
 * @library /testlibrary /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:-UseFieldFlattening -XX:-UseArrayFlattening
 *                   -XX:CompileCommand=dontinline,*::incrementAndCheck*
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   compiler.valhalla.inlinetypes.TestTearing
 */

/*
 * @test id=no-flattening-di-AII
 * @key randomness
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 * @summary Detect tearing on flat accesses and buffering.
 * @library /testlibrary /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:-UseFieldFlattening -XX:-UseArrayFlattening
 *                   -XX:CompileCommand=dontinline,*::incrementAndCheck*
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *                   compiler.valhalla.inlinetypes.TestTearing
 */

/*
 * @test id=xcomp-no-stress
 * @key randomness
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 * @summary Detect tearing on flat accesses and buffering.
 * @library /testlibrary /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *                   -XX:+WhiteBoxAPI -XX:+UseNullableAtomicValueFlattening -XX:+UseNullFreeAtomicValueFlattening -XX:+UseArrayFlattening
 *                   -Xcomp -XX:-TieredCompilation
 *                   compiler.valhalla.inlinetypes.TestTearing
 */

/*
 * @test id=flattening
 * @key randomness
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 * @summary Detect tearing on flat accesses and buffering.
 * @library /testlibrary /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *                   -XX:+WhiteBoxAPI -XX:+UseNullableAtomicValueFlattening -XX:+UseNullFreeAtomicValueFlattening -XX:+UseArrayFlattening
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   compiler.valhalla.inlinetypes.TestTearing
 */

/*
 * @test id=flattening-AII
 * @key randomness
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 * @summary Detect tearing on flat accesses and buffering.
 * @library /testlibrary /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *                   -XX:+WhiteBoxAPI -XX:+UseNullableAtomicValueFlattening -XX:+UseNullFreeAtomicValueFlattening -XX:+UseArrayFlattening
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *                   compiler.valhalla.inlinetypes.TestTearing
 */

/*
 * @test id=flattening-di
 * @key randomness
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 * @summary Detect tearing on flat accesses and buffering.
 * @library /testlibrary /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *                   -XX:+WhiteBoxAPI -XX:+UseNullableAtomicValueFlattening -XX:+UseNullFreeAtomicValueFlattening -XX:+UseArrayFlattening
 *                   -XX:CompileCommand=dontinline,*::incrementAndCheck*
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   compiler.valhalla.inlinetypes.TestTearing
 */

/*
 * @test id=flattening-di-AII
 * @key randomness
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 * @summary Detect tearing on flat accesses and buffering.
 * @library /testlibrary /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *                   -XX:+WhiteBoxAPI -XX:+UseNullableAtomicValueFlattening -XX:+UseNullFreeAtomicValueFlattening -XX:+UseArrayFlattening
 *                   -XX:CompileCommand=dontinline,*::incrementAndCheck*
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *                   compiler.valhalla.inlinetypes.TestTearing
 */

@LooselyConsistentValue
value class MyValueTearing {
    // Make sure the payload size is <= 64-bit to enable atomic flattening
    short x;
    short y;

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long X_OFFSET;
    private static final long Y_OFFSET;
    static {
        try {
            Field xField = MyValueTearing.class.getDeclaredField("x");
            X_OFFSET = U.objectFieldOffset(xField);
            Field yField = MyValueTearing.class.getDeclaredField("y");
            Y_OFFSET = U.objectFieldOffset(yField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static final MyValueTearing DEFAULT = new MyValueTearing((short)0, (short)0);

    MyValueTearing(short x, short y) {
        this.x = x;
        this.y = y;
    }

    MyValueTearing incrementAndCheck() {
        Asserts.assertEQ(x, y, "Inconsistent field values");
        return new MyValueTearing((short)(x + 1), (short)(y + 1));
    }

    MyValueTearing incrementAndCheckUnsafe() {
        Asserts.assertEQ(x, y, "Inconsistent field values");
        MyValueTearing[] vt = (MyValueTearing[]) ValueClass.newNullRestrictedAtomicArray(MyValueTearing.class, 1, this);
        long baseOffset = U.arrayInstanceBaseOffset(vt) - U.valueHeaderSize(MyValueTearing.class);
        U.putShort(vt, baseOffset + X_OFFSET, (short)(x + 1));
        U.putShort(vt, baseOffset + Y_OFFSET, (short)(y + 1));
        return vt[0];
    }
}

public class TestTearing {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final boolean SLOW_CONFIGURATION =
        (WHITE_BOX.getIntxVMFlag("TieredStopAtLevel").intValue() < 4) ||
         WHITE_BOX.getBooleanVMFlag("SafepointALot") ||
         WHITE_BOX.getBooleanVMFlag("DeoptimizeALot") ||
         WHITE_BOX.getBooleanVMFlag("DeoptimizeNMethodBarriersALot") ||
        !WHITE_BOX.getBooleanVMFlag("UseTLAB") ||
         WHITE_BOX.getBooleanVMFlag("VerifyOops") ||
         WHITE_BOX.getBooleanVMFlag("CheckUnhandledOops");
    private static final boolean HAS_FLAT_ARRAY = WHITE_BOX.getBooleanVMFlag("UseArrayFlattening");

    // Null-free, volatile -> atomic access
    @NullRestricted
    volatile static MyValueTearing field1 = MyValueTearing.DEFAULT;
    @NullRestricted
    volatile MyValueTearing field2;

    // Nullable fields are always atomic
    static MyValueTearing field3 = new MyValueTearing((short)0, (short)0);
    MyValueTearing field4 = new MyValueTearing((short)0, (short)0);

    // Final arrays
    static final MyValueTearing[] array1 = (MyValueTearing[])ValueClass.newNullRestrictedAtomicArray(MyValueTearing.class, 1, MyValueTearing.DEFAULT);
    static final MyValueTearing[] array2 = (MyValueTearing[])ValueClass.newNullableAtomicArray(MyValueTearing.class, 1);
    static {
        array2[0] = new MyValueTearing((short)0, (short)0);
    }
    static final MyValueTearing[] array3 = new MyValueTearing[] { new MyValueTearing((short)0, (short)0) };

    // Non-final arrays
    static MyValueTearing[] array4 = (MyValueTearing[])ValueClass.newNullRestrictedAtomicArray(MyValueTearing.class, 1, MyValueTearing.DEFAULT);
    static MyValueTearing[] array5 = (MyValueTearing[])ValueClass.newNullableAtomicArray(MyValueTearing.class, 1);
    static {
        array5[0] = new MyValueTearing((short)0, (short)0);
    }
    static MyValueTearing[] array6 = new MyValueTearing[] { new MyValueTearing((short)0, (short)0) };

    // Object arrays
    static Object[] array7 = (MyValueTearing[])ValueClass.newNullRestrictedAtomicArray(MyValueTearing.class, 1, MyValueTearing.DEFAULT);
    static Object[] array8 = (MyValueTearing[])ValueClass.newNullableAtomicArray(MyValueTearing.class, 1);
    static {
        array8[0] = new MyValueTearing((short)0, (short)0);
    }
    static Object[] array9 = new MyValueTearing[] { new MyValueTearing((short)0, (short)0) };

    // Object arrays stored in volatile fields (for safe publication)
    static volatile Object[] array10 = (MyValueTearing[])ValueClass.newNullRestrictedAtomicArray(MyValueTearing.class, 1, MyValueTearing.DEFAULT);
    static volatile Object[] array11 = (MyValueTearing[])ValueClass.newNullableAtomicArray(MyValueTearing.class, 1);
    static {
        array11[0] = new MyValueTearing((short)0, (short)0);
    }
    static volatile Object[] array12 = new MyValueTearing[] { new MyValueTearing((short)0, (short)0) };

    static final MethodHandle incrementAndCheck_mh;

    static {
        try {
            Class<?> clazz = MyValueTearing.class;
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType mt = MethodType.methodType(MyValueTearing.class);
            incrementAndCheck_mh = lookup.findVirtual(clazz, "incrementAndCheck", mt);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Method handle lookup failed");
        }
    }

    public TestTearing() {
        field2 = MyValueTearing.DEFAULT;
        super();
    }

    static class Runner extends Thread {
        TestTearing test;
        private final int loopLimit;

        public Runner(TestTearing test, int loopLimit) {
            this.test = test;
            this.loopLimit = loopLimit;
        }

        public void run() {
            for (int i = 0; i < loopLimit; ++i) {
                // Create "local" arrays so that C2 has full type info
                MyValueTearing[] localArray1 = (MyValueTearing[])ValueClass.newNullRestrictedAtomicArray(MyValueTearing.class, 1, MyValueTearing.DEFAULT);
                MyValueTearing[] localArray2 = (MyValueTearing[])ValueClass.newNullableAtomicArray(MyValueTearing.class, 1);
                localArray2[0] = new MyValueTearing((short)0, (short)0);
                MyValueTearing[] localArray3 = new MyValueTearing[] { new MyValueTearing((short)0, (short)0) };

                Asserts.assertTrue(ValueClass.isAtomicArray(localArray1));
                Asserts.assertTrue(ValueClass.isAtomicArray(localArray2));
                Asserts.assertTrue(ValueClass.isAtomicArray(localArray3));
                Asserts.assertTrue(ValueClass.isNullRestrictedArray(localArray1));
                Asserts.assertFalse(ValueClass.isNullRestrictedArray(localArray2));
                Asserts.assertFalse(ValueClass.isNullRestrictedArray(localArray3));

                // Let them escape safely via a volatile field
                array10 = localArray1;
                array11 = localArray2;
                array12 = localArray3;

                localArray1[0] = localArray1[0].incrementAndCheck();
                localArray2[0] = localArray2[0].incrementAndCheck();
                localArray3[0] = localArray3[0].incrementAndCheck();

                test.field1 = test.field1.incrementAndCheck();
                test.field2 = test.field2.incrementAndCheck();
                test.field3 = test.field3.incrementAndCheck();
                test.field4 = test.field4.incrementAndCheck();
                array1[0] = array1[0].incrementAndCheck();
                array2[0] = array2[0].incrementAndCheck();
                array3[0] = array3[0].incrementAndCheck();
                array4[0] = array4[0].incrementAndCheck();
                array5[0] = array5[0].incrementAndCheck();
                array6[0] = array6[0].incrementAndCheck();
                array7[0] = ((MyValueTearing)array7[0]).incrementAndCheck();
                array8[0] = ((MyValueTearing)array8[0]).incrementAndCheck();
                array9[0] = ((MyValueTearing)array9[0]).incrementAndCheck();
                array10[0] = ((MyValueTearing)array10[0]).incrementAndCheck();
                array11[0] = ((MyValueTearing)array11[0]).incrementAndCheck();
                array12[0] = ((MyValueTearing)array12[0]).incrementAndCheck();

                if (HAS_FLAT_ARRAY) {
                    test.field1 = test.field1.incrementAndCheckUnsafe();
                    test.field2 = test.field2.incrementAndCheckUnsafe();
                    test.field3 = test.field3.incrementAndCheckUnsafe();
                    test.field4 = test.field4.incrementAndCheckUnsafe();
                    array1[0] = array1[0].incrementAndCheckUnsafe();
                    array2[0] = array2[0].incrementAndCheckUnsafe();
                    array3[0] = array3[0].incrementAndCheckUnsafe();
                    array4[0] = array4[0].incrementAndCheckUnsafe();
                    array5[0] = array5[0].incrementAndCheckUnsafe();
                    array6[0] = array6[0].incrementAndCheckUnsafe();
                    array7[0] = ((MyValueTearing) array7[0]).incrementAndCheckUnsafe();
                    array8[0] = ((MyValueTearing) array8[0]).incrementAndCheckUnsafe();
                    array9[0] = ((MyValueTearing) array9[0]).incrementAndCheckUnsafe();
                    array10[0] = ((MyValueTearing) array10[0]).incrementAndCheckUnsafe();
                    array11[0] = ((MyValueTearing) array11[0]).incrementAndCheckUnsafe();
                    array12[0] = ((MyValueTearing) array12[0]).incrementAndCheckUnsafe();
                }

                try {
                    test.field1 = (MyValueTearing)incrementAndCheck_mh.invokeExact(test.field1);
                    test.field2 = (MyValueTearing)incrementAndCheck_mh.invokeExact(test.field2);
                    test.field3 = (MyValueTearing)incrementAndCheck_mh.invokeExact(test.field1);
                    test.field4 = (MyValueTearing)incrementAndCheck_mh.invokeExact(test.field2);
                    array1[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact(array1[0]);
                    array2[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact(array2[0]);
                    array3[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact(array3[0]);
                    array4[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact(array4[0]);
                    array5[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact(array5[0]);
                    array6[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact(array6[0]);
                    array7[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact((MyValueTearing)array7[0]);
                    array8[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact((MyValueTearing)array8[0]);
                    array9[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact((MyValueTearing)array9[0]);
                    array10[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact((MyValueTearing)array10[0]);
                    array11[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact((MyValueTearing)array11[0]);
                    array12[0] = (MyValueTearing)incrementAndCheck_mh.invokeExact((MyValueTearing)array12[0]);
                } catch (Throwable t) {
                    throw new RuntimeException("Test failed", t);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Create threads that concurrently update some value class (array) fields
        // and check the fields of the value classes for consistency to detect tearing.
        int loopLimit = 250_000;
        if (SLOW_CONFIGURATION) {
            // Lower the number of iterations in slow configurations
            loopLimit = 50_000;
        }
        TestTearing test = new TestTearing();
        Thread runner = null;
        for (int i = 0; i < 10; ++i) {
            runner = new Runner(test, loopLimit);
            runner.start();
        }
        runner.join();
    }
}
