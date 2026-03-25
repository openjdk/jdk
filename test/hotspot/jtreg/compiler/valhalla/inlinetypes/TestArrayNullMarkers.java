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

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

/*
 * @test id=default
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

/*
 * @test id=no-flattening
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                               -XX:-UseNullableValueFlattening -XX:-UseAtomicValueFlattening -XX:-UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

/*
 * @test id=nAVF
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                               -XX:-UseNullableValueFlattening -XX:-UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

/*
 * @test id=AVF
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                               -XX:-UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:-UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

/*
 * @test id=AVF-nAVF
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                               -XX:-UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

/*
 * @test id=NVF
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:-UseAtomicValueFlattening -XX:-UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

/*
 * @test id=NVF-nAVF
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:-UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

/*
 * @test id=NVF-AVF
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:-UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

/*
 * @test id=all-flattening
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

/*
 * @test id=all-flattening-di
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               -XX:CompileCommand=dontinline,*::test* -XX:CompileCommand=dontinline,*::check*
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

/*
 * @test id=all-flattening-restrict-profiling
 * @summary Test support for null markers in (flat) arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               -XX:+IgnoreUnrecognizedVMOptions -XX:-MonomorphicArrayCheck -XX:-UseArrayLoadStoreProfile
 *                               compiler.valhalla.inlinetypes.TestArrayNullMarkers
 */

public class TestArrayNullMarkers {

    private static final WhiteBox WHITEBOX = WhiteBox.getWhiteBox();
    private static final boolean UseArrayFlattening = WHITEBOX.getBooleanVMFlag("UseArrayFlattening");
    private static final boolean UseNullableValueFlattening = WHITEBOX.getBooleanVMFlag("UseNullableValueFlattening");
    private static final boolean UseNonAtomicValueFlattening = WHITEBOX.getBooleanVMFlag("UseNonAtomicValueFlattening");
    private static final boolean UseAtomicValueFlattening = WHITEBOX.getBooleanVMFlag("UseAtomicValueFlattening");
    private static final boolean ForceNonTearable = !WHITEBOX.getStringVMFlag("ForceNonTearable").equals("");

    // Is naturally atomic and has null-free, non-atomic, flat (1 bytes), null-free, atomic, flat (1 bytes) and nullable, atomic, flat (4 bytes) layouts
    @LooselyConsistentValue
    static value class OneByte {
        byte b;

        public OneByte(byte b) {
            this.b = b;
        }

        static final OneByte DEFAULT = new OneByte((byte)0);
    }

    // Has null-free, non-atomic, flat (2 bytes), null-free, atomic, flat (2 bytes) and nullable, atomic, flat (4 bytes) layouts
    @LooselyConsistentValue
    static value class TwoBytes {
        byte b1;
        byte b2;

        public TwoBytes(byte b1, byte b2) {
            this.b1 = b1;
            this.b2 = b2;
        }

        static final TwoBytes DEFAULT = new TwoBytes((byte)0, (byte)0);
    }

    // Has null-free, non-atomic, flat (4 bytes), null-free, atomic, flat (4 bytes) and nullable, atomic, flat (8 bytes) layouts
    @LooselyConsistentValue
    static value class TwoShorts {
        short s1;
        short s2;

        public TwoShorts(short s1, short s2) {
            this.s1 = s1;
            this.s2 = s2;
        }

        static final TwoShorts DEFAULT = new TwoShorts((short)0, (short)0);
    }

    // Has null-free, non-atomic flat (8 bytes) and null-free, atomic, flat (8 bytes) layouts
    @LooselyConsistentValue
    static value class TwoInts {
        int i1;
        int i2;

        public TwoInts(int i1, int i2) {
            this.i1 = i1;
            this.i2 = i2;
        }

        static final TwoInts DEFAULT = new TwoInts(0, 0);
    }

    // Has null-free, non-atomic flat (16 bytes) layout
    @LooselyConsistentValue
    static value class TwoLongs {
        long l1;
        long l2;

        public TwoLongs(int l1, int l2) {
            this.l1 = l1;
            this.l2 = l2;
        }

        static final TwoLongs DEFAULT = new TwoLongs(0, 0);
    }

    // Has null-free, non-atomic, flat (5 bytes), null-free, atomic, flat (8 bytes) and nullable, atomic, flat (8 bytes) layouts
    @LooselyConsistentValue
    static value class ByteAndOop {
        byte b;
        MyClass obj;

        public ByteAndOop(byte b, MyClass obj) {
            this.b = b;
            this.obj = obj;
        }

        static final ByteAndOop DEFAULT = new ByteAndOop((byte)0, null);
    }

    static class MyClass {
        int x;

        public MyClass(int x) {
            this.x = x;
        }
    }

    @LooselyConsistentValue
    static value class IntAndArrayOop {
        int i;
        MyClass[] array;

        public IntAndArrayOop(int i, MyClass[] array) {
            this.i = i;
            this.array = array;
        }

        static final IntAndArrayOop DEFAULT = new IntAndArrayOop(0, null);
    }

    public static void testWrite0(OneByte[] array, int i, OneByte val) {
        array[i] = val;
    }

    public static void testWrite1(TwoBytes[] array, int i, TwoBytes val) {
        array[i] = val;
    }

    public static void testWrite2(TwoShorts[] array, int i, TwoShorts val) {
        array[i] = val;
    }

    public static void testWrite3(TwoInts[] array, int i, TwoInts val) {
        array[i] = val;
    }

    public static void testWrite4(TwoLongs[] array, int i, TwoLongs val) {
        array[i] = val;
    }

    public static void testWrite5(ByteAndOop[] array, int i, ByteAndOop val) {
        array[i] = val;
    }

    public static void testWrite6(Object[] array, int i, Object val) {
        array[i] = val;
    }

    public static OneByte testRead0(OneByte[] array, int i) {
        return array[i];
    }

    public static TwoBytes testRead1(TwoBytes[] array, int i) {
        return array[i];
    }

    public static TwoShorts testRead2(TwoShorts[] array, int i) {
        return array[i];
    }

    public static TwoInts testRead3(TwoInts[] array, int i) {
        return array[i];
    }

    public static TwoLongs testRead4(TwoLongs[] array, int i) {
        return array[i];
    }

    public static ByteAndOop testRead5(ByteAndOop[] array, int i) {
        return array[i];
    }

    public static Object testRead6(Object[] array, int i) {
        return array[i];
    }

    static final OneByte CANARY0 = new OneByte((byte)42);

    public static void checkCanary0(OneByte[] array) {
        Asserts.assertEQ(array[0], CANARY0);
        Asserts.assertEQ(array[2], CANARY0);
    }

    static final TwoBytes CANARY1 = new TwoBytes((byte)42, (byte)42);

    public static void checkCanary1(TwoBytes[] array) {
        Asserts.assertEQ(array[0], CANARY1);
        Asserts.assertEQ(array[2], CANARY1);
    }

    static final TwoShorts CANARY2 = new TwoShorts((short)42, (short)42);

    public static void checkCanary2(TwoShorts[] array) {
        Asserts.assertEQ(array[0], CANARY2);
        Asserts.assertEQ(array[2], CANARY2);
    }

    static final TwoInts CANARY3 = new TwoInts(42, 42);

    public static void checkCanary3(TwoInts[] array) {
        Asserts.assertEQ(array[0], CANARY3);
        Asserts.assertEQ(array[2], CANARY3);
    }

    static final TwoLongs CANARY4 = new TwoLongs(42, 42);

    public static void checkCanary4(TwoLongs[] array) {
        Asserts.assertEQ(array[0], CANARY4);
        Asserts.assertEQ(array[2], CANARY4);
    }

    static final ByteAndOop CANARY5 = new ByteAndOop((byte)42, new MyClass(42));

    public static void checkCanary5(ByteAndOop[] array) {
        Asserts.assertEQ(array[0], CANARY5);
        Asserts.assertEQ(array[2], CANARY5);
    }

    public static TwoBytes[] testNullRestrictedArrayIntrinsic(int size, int idx, TwoBytes val) {
        TwoBytes[] nullFreeArray = (TwoBytes[])ValueClass.newNullRestrictedNonAtomicArray(TwoBytes.class, size, TwoBytes.DEFAULT);
        if (!ForceNonTearable) {
            Asserts.assertEquals(ValueClass.isFlatArray(nullFreeArray), UseArrayFlattening && (UseNonAtomicValueFlattening || UseAtomicValueFlattening));
        }
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(nullFreeArray));
        Asserts.assertEquals(nullFreeArray[idx], TwoBytes.DEFAULT);
        testWrite1(nullFreeArray, idx, val);
        Asserts.assertEQ(testRead1(nullFreeArray, idx), val);
        return nullFreeArray;
    }

    // Non-final value to initialize null-restricted arrays
    static Object initVal1 = CANARY1;
    static TwoBytes initVal2 = CANARY1;

    public static TwoBytes[] testNullRestrictedArrayIntrinsicDynamic1(int size, int idx, TwoBytes val) {
        TwoBytes[] nullFreeArray = (TwoBytes[])ValueClass.newNullRestrictedNonAtomicArray(TwoBytes.class, size, initVal1);
        if (!ForceNonTearable) {
            Asserts.assertEquals(ValueClass.isFlatArray(nullFreeArray), UseArrayFlattening && (UseNonAtomicValueFlattening || UseAtomicValueFlattening));
        }
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(nullFreeArray));
        Asserts.assertEquals(nullFreeArray[idx], CANARY1);
        testWrite1(nullFreeArray, idx, val);
        Asserts.assertEQ(testRead1(nullFreeArray, idx), val);
        return nullFreeArray;
    }

    public static TwoBytes[] testNullRestrictedArrayIntrinsicDynamic2(int size, int idx, TwoBytes val) {
        TwoBytes[] nullFreeArray = (TwoBytes[])ValueClass.newNullRestrictedNonAtomicArray(TwoBytes.class, size, initVal2);
        if (!ForceNonTearable) {
            Asserts.assertEquals(ValueClass.isFlatArray(nullFreeArray), UseArrayFlattening && (UseNonAtomicValueFlattening || UseAtomicValueFlattening));
        }
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(nullFreeArray));
        Asserts.assertEquals(nullFreeArray[idx], CANARY1);
        testWrite1(nullFreeArray, idx, val);
        Asserts.assertEQ(testRead1(nullFreeArray, idx), val);
        return nullFreeArray;
    }

    static byte myByte = 0;

    public static TwoBytes[] testNullRestrictedArrayIntrinsicDynamic3(int size, int idx, TwoBytes val) {
        TwoBytes[] nullFreeArray = (TwoBytes[])ValueClass.newNullRestrictedNonAtomicArray(TwoBytes.class, size, new TwoBytes(++myByte, myByte));
        if (!ForceNonTearable) {
            Asserts.assertEquals(ValueClass.isFlatArray(nullFreeArray), UseArrayFlattening && (UseNonAtomicValueFlattening || UseAtomicValueFlattening));
        }
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(nullFreeArray));
        Asserts.assertEquals(nullFreeArray[idx], new TwoBytes(myByte, myByte));
        testWrite1(nullFreeArray, idx, val);
        Asserts.assertEQ(testRead1(nullFreeArray, idx), val);
        return nullFreeArray;
    }

    public static TwoBytes[] testNullRestrictedAtomicArrayIntrinsic(int size, int idx, TwoBytes val) {
        TwoBytes[] nullFreeAtomicArray = (TwoBytes[])ValueClass.newNullRestrictedAtomicArray(TwoBytes.class, size, TwoBytes.DEFAULT);
        if (!ForceNonTearable) {
            Asserts.assertEquals(ValueClass.isFlatArray(nullFreeAtomicArray), UseArrayFlattening && UseAtomicValueFlattening);
        }
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(nullFreeAtomicArray));
        Asserts.assertTrue(ValueClass.isAtomicArray(nullFreeAtomicArray));
        Asserts.assertEquals(nullFreeAtomicArray[idx], TwoBytes.DEFAULT);
        testWrite1(nullFreeAtomicArray, idx, val);
        Asserts.assertEQ(testRead1(nullFreeAtomicArray, idx), val);
        return nullFreeAtomicArray;
    }

    public static TwoBytes[] testNullRestrictedAtomicArrayIntrinsicDynamic1(int size, int idx, TwoBytes val) {
        TwoBytes[] nullFreeAtomicArray = (TwoBytes[])ValueClass.newNullRestrictedAtomicArray(TwoBytes.class, size, initVal1);
        if (!ForceNonTearable) {
            Asserts.assertEquals(ValueClass.isFlatArray(nullFreeAtomicArray), UseArrayFlattening && UseAtomicValueFlattening);
        }
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(nullFreeAtomicArray));
        Asserts.assertTrue(ValueClass.isAtomicArray(nullFreeAtomicArray));
        Asserts.assertEquals(nullFreeAtomicArray[idx], CANARY1);
        testWrite1(nullFreeAtomicArray, idx, val);
        Asserts.assertEQ(testRead1(nullFreeAtomicArray, idx), val);
        return nullFreeAtomicArray;
    }

    public static TwoBytes[] testNullRestrictedAtomicArrayIntrinsicDynamic2(int size, int idx, TwoBytes val) {
        TwoBytes[] nullFreeAtomicArray = (TwoBytes[])ValueClass.newNullRestrictedAtomicArray(TwoBytes.class, size, initVal2);
        if (!ForceNonTearable) {
            Asserts.assertEquals(ValueClass.isFlatArray(nullFreeAtomicArray), UseArrayFlattening && UseAtomicValueFlattening);
        }
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(nullFreeAtomicArray));
        Asserts.assertTrue(ValueClass.isAtomicArray(nullFreeAtomicArray));
        Asserts.assertEquals(nullFreeAtomicArray[idx], CANARY1);
        testWrite1(nullFreeAtomicArray, idx, val);
        Asserts.assertEQ(testRead1(nullFreeAtomicArray, idx), val);
        return nullFreeAtomicArray;
    }

    public static TwoBytes[] testNullRestrictedAtomicArrayIntrinsicDynamic3(int size, int idx, TwoBytes val) {
        TwoBytes[] nullFreeAtomicArray = (TwoBytes[])ValueClass.newNullRestrictedAtomicArray(TwoBytes.class, size, new TwoBytes(++myByte, myByte));
        if (!ForceNonTearable) {
            Asserts.assertEquals(ValueClass.isFlatArray(nullFreeAtomicArray), UseArrayFlattening && UseAtomicValueFlattening);
        }
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(nullFreeAtomicArray));
        Asserts.assertTrue(ValueClass.isAtomicArray(nullFreeAtomicArray));
        Asserts.assertEquals(nullFreeAtomicArray[idx], new TwoBytes(myByte, myByte));
        testWrite1(nullFreeAtomicArray, idx, val);
        Asserts.assertEQ(testRead1(nullFreeAtomicArray, idx), val);
        return nullFreeAtomicArray;
    }

    public static TwoBytes[] testNullableAtomicArrayIntrinsic(int size, int idx, TwoBytes val) {
        TwoBytes[] nullableAtomicArray = (TwoBytes[])ValueClass.newNullableAtomicArray(TwoBytes.class, size);
        if (!ForceNonTearable) {
            Asserts.assertEquals(ValueClass.isFlatArray(nullableAtomicArray), UseArrayFlattening && UseNullableValueFlattening);
        }
        Asserts.assertFalse(ValueClass.isNullRestrictedArray(nullableAtomicArray));
        Asserts.assertTrue(ValueClass.isAtomicArray(nullableAtomicArray));
        Asserts.assertEquals(nullableAtomicArray[idx], null);
        testWrite1(nullableAtomicArray, idx, val);
        Asserts.assertEQ(testRead1(nullableAtomicArray, idx), val);
        return nullableAtomicArray;
    }

    @LooselyConsistentValue
    static value class MyValueEmpty {
        static final MyValueEmpty DEFAULT = new MyValueEmpty();
    }

    @LooselyConsistentValue
    static value class ValueHolder1 {
        TwoBytes val;

        public ValueHolder1(TwoBytes val) {
            this.val = val;
        }

        static final ValueHolder1 DEFAULT = new ValueHolder1(null);
    }

    // Test support for replaced arrays
    public static void testScalarReplacement1(OneByte valNullFree, OneByte val, boolean trap) {
        OneByte[] nullFreeArray = (OneByte[])ValueClass.newNullRestrictedNonAtomicArray(OneByte.class, 2, OneByte.DEFAULT);
        nullFreeArray[0] = valNullFree;
        nullFreeArray[1] = new OneByte((byte)42);
        if (trap) {
            Asserts.assertEQ(nullFreeArray[0], valNullFree);
            Asserts.assertEQ(nullFreeArray[1], new OneByte((byte)42));
        }
    }

    public static void testScalarReplacement2(OneByte valNullFree, OneByte val, boolean trap) {
        OneByte[] nullFreeAtomicArray = (OneByte[])ValueClass.newNullRestrictedAtomicArray(OneByte.class, 2, OneByte.DEFAULT);
        nullFreeAtomicArray[0] = valNullFree;
        nullFreeAtomicArray[1] = new OneByte((byte)42);
        if (trap) {
            Asserts.assertEQ(nullFreeAtomicArray[0], valNullFree);
            Asserts.assertEQ(nullFreeAtomicArray[1], new OneByte((byte)42));
        }
    }

    public static void testScalarReplacement3(OneByte valNullFree, OneByte val, boolean trap) {
        OneByte[] nullableAtomicArray = (OneByte[])ValueClass.newNullableAtomicArray(OneByte.class, 4);
        nullableAtomicArray[0] = valNullFree;
        nullableAtomicArray[1] = val;
        nullableAtomicArray[2] = new OneByte((byte)42);
        nullableAtomicArray[3] = null;
        if (trap) {
            Asserts.assertEQ(nullableAtomicArray[0], valNullFree);
            Asserts.assertEQ(nullableAtomicArray[1], val);
            Asserts.assertEQ(nullableAtomicArray[2], new OneByte((byte)42));
            Asserts.assertEQ(nullableAtomicArray[3], null);
        }
    }

    public static void testScalarReplacement4(OneByte valNullFree, OneByte val, boolean trap) {
        OneByte[] nullableArray = new OneByte[4];
        nullableArray[0] = valNullFree;
        nullableArray[1] = val;
        nullableArray[2] = new OneByte((byte)42);
        nullableArray[3] = null;
        if (trap) {
            Asserts.assertEQ(nullableArray[0], valNullFree);
            Asserts.assertEQ(nullableArray[1], val);
            Asserts.assertEQ(nullableArray[2], new OneByte((byte)42));
            Asserts.assertEQ(nullableArray[3], null);
        }
    }

    public static void testScalarReplacement5(TwoBytes val, boolean trap) {
        ValueHolder1[] nullFreeArray = (ValueHolder1[])ValueClass.newNullRestrictedNonAtomicArray(ValueHolder1.class, 1, ValueHolder1.DEFAULT);
        nullFreeArray[0] = new ValueHolder1(val);
        if (trap) {
            Asserts.assertEQ(nullFreeArray[0].val, val);
        }
    }

    public static void testScalarReplacement6(TwoBytes val, boolean trap) {
        ValueHolder1[] nullFreeAtomicArray = (ValueHolder1[])ValueClass.newNullRestrictedAtomicArray(ValueHolder1.class, 1, ValueHolder1.DEFAULT);
        nullFreeAtomicArray[0] = new ValueHolder1(val);
        if (trap) {
            Asserts.assertEQ(nullFreeAtomicArray[0].val, val);
        }
    }

    public static void testScalarReplacement7(TwoBytes val, boolean trap) {
        ValueHolder1[] nullableAtomicArray = (ValueHolder1[])ValueClass.newNullableAtomicArray(ValueHolder1.class, 2);
        nullableAtomicArray[0] = new ValueHolder1(val);
        nullableAtomicArray[1] = ValueHolder1.DEFAULT;
        if (trap) {
            Asserts.assertEQ(nullableAtomicArray[0].val, val);
            Asserts.assertEQ(nullableAtomicArray[1].val, null);
        }
    }

    public static void testScalarReplacement8(TwoBytes val, boolean trap) {
        ValueHolder1[] nullableArray = new ValueHolder1[2];
        nullableArray[0] = new ValueHolder1(val);
        nullableArray[1] = ValueHolder1.DEFAULT;
        if (trap) {
            Asserts.assertEQ(nullableArray[0].val, val);
            Asserts.assertEQ(nullableArray[1].val, null);
        }
    }

    static void produceGarbage() {
        for (int i = 0; i < 100; ++i) {
            Object[] arrays = new Object[1024];
            for (int j = 0; j < arrays.length; j++) {
                arrays[j] = new int[1024];
            }
        }
        System.gc();
    }

    static TwoShorts[] array1 = (TwoShorts[])ValueClass.newNullRestrictedAtomicArray(TwoShorts.class, 1, TwoShorts.DEFAULT);
    static TwoShorts[] array2 = (TwoShorts[])ValueClass.newNullableAtomicArray(TwoShorts.class, 1);
    static {
        array2[0] = TwoShorts.DEFAULT;
    }
    static TwoShorts[] array3 = new TwoShorts[] { TwoShorts.DEFAULT };

    // Catches an issue with type speculation based on profiling
    public static void testProfiling() {
        array1[0] = new TwoShorts(array1[0].s1, (short)0);
        array2[0] = new TwoShorts(array2[0].s1, (short)0);
        array3[0] = new TwoShorts(array3[0].s1, (short)0);
    }

    static final OneByte[] NULL_FREE_ARRAY_0 = (OneByte[])ValueClass.newNullRestrictedNonAtomicArray(OneByte.class, 2, OneByte.DEFAULT);
    static final OneByte[] NULL_FREE_ATOMIC_ARRAY_0 = (OneByte[])ValueClass.newNullRestrictedAtomicArray(OneByte.class, 2, OneByte.DEFAULT);
    static final OneByte[] NULLABLE_ARRAY_0 = new OneByte[2];
    static final OneByte[] NULLABLE_ATOMIC_ARRAY_0 = (OneByte[])ValueClass.newNullableAtomicArray(OneByte.class, 2);

    static final TwoBytes[] NULL_FREE_ARRAY_1 = (TwoBytes[])ValueClass.newNullRestrictedNonAtomicArray(TwoBytes.class, 2, TwoBytes.DEFAULT);
    static final TwoBytes[] NULL_FREE_ATOMIC_ARRAY_1 = (TwoBytes[])ValueClass.newNullRestrictedAtomicArray(TwoBytes.class, 2, TwoBytes.DEFAULT);
    static final TwoBytes[] NULLABLE_ARRAY_1 = new TwoBytes[2];
    static final TwoBytes[] NULLABLE_ATOMIC_ARRAY_1 = (TwoBytes[])ValueClass.newNullableAtomicArray(TwoBytes.class, 2);

    static final TwoShorts[] NULL_FREE_ARRAY_2 = (TwoShorts[])ValueClass.newNullRestrictedNonAtomicArray(TwoShorts.class, 2, TwoShorts.DEFAULT);
    static final TwoShorts[] NULL_FREE_ATOMIC_ARRAY_2 = (TwoShorts[])ValueClass.newNullRestrictedAtomicArray(TwoShorts.class, 2, TwoShorts.DEFAULT);
    static final TwoShorts[] NULLABLE_ARRAY_2 = new TwoShorts[2];
    static final TwoShorts[] NULLABLE_ATOMIC_ARRAY_2 = (TwoShorts[])ValueClass.newNullableAtomicArray(TwoShorts.class, 2);

    static final TwoInts[] NULL_FREE_ARRAY_3 = (TwoInts[])ValueClass.newNullRestrictedNonAtomicArray(TwoInts.class, 1, TwoInts.DEFAULT);
    static final TwoInts[] NULL_FREE_ATOMIC_ARRAY_3 = (TwoInts[])ValueClass.newNullRestrictedAtomicArray(TwoInts.class, 1, TwoInts.DEFAULT);
    static final TwoInts[] NULLABLE_ARRAY_3 = new TwoInts[1];
    static final TwoInts[] NULLABLE_ATOMIC_ARRAY_3 = (TwoInts[])ValueClass.newNullableAtomicArray(TwoInts.class, 1);

    static final TwoLongs[] NULL_FREE_ARRAY_4 = (TwoLongs[])ValueClass.newNullRestrictedNonAtomicArray(TwoLongs.class, 1, TwoLongs.DEFAULT);
    static final TwoLongs[] NULL_FREE_ATOMIC_ARRAY_4 = (TwoLongs[])ValueClass.newNullRestrictedAtomicArray(TwoLongs.class, 1, TwoLongs.DEFAULT);
    static final TwoLongs[] NULLABLE_ARRAY_4 = new TwoLongs[1];
    static final TwoLongs[] NULLABLE_ATOMIC_ARRAY_4 = (TwoLongs[])ValueClass.newNullableAtomicArray(TwoLongs.class, 1);

    static final ByteAndOop[] NULL_FREE_ARRAY_5 = (ByteAndOop[])ValueClass.newNullRestrictedNonAtomicArray(ByteAndOop.class, 1, ByteAndOop.DEFAULT);
    static final ByteAndOop[] NULL_FREE_ATOMIC_ARRAY_5 = (ByteAndOop[])ValueClass.newNullRestrictedAtomicArray(ByteAndOop.class, 1, ByteAndOop.DEFAULT);
    static final ByteAndOop[] NULLABLE_ARRAY_5 = new ByteAndOop[1];
    static final ByteAndOop[] NULLABLE_ATOMIC_ARRAY_5 = (ByteAndOop[])ValueClass.newNullableAtomicArray(ByteAndOop.class, 1);

    // Test access to constant arrays
    public static void testConstantArrays(int i) {
        OneByte val0 = new OneByte((byte)i);
        Asserts.assertEQ(NULL_FREE_ARRAY_0[0], OneByte.DEFAULT);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_0[0], OneByte.DEFAULT);
        Asserts.assertEQ(NULLABLE_ARRAY_0[0], null);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_0[0], null);

        try {
            NULL_FREE_ARRAY_0[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            NULL_FREE_ATOMIC_ARRAY_0[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }

        NULL_FREE_ARRAY_0[0] = val0;
        NULL_FREE_ATOMIC_ARRAY_0[0] = val0;
        NULLABLE_ARRAY_0[0] = val0;
        NULLABLE_ATOMIC_ARRAY_0[0] = val0;

        Asserts.assertEQ(NULL_FREE_ARRAY_0[0], val0);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_0[0], val0);
        Asserts.assertEQ(NULLABLE_ARRAY_0[0], val0);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_0[0], val0);

        NULL_FREE_ARRAY_0[0] = OneByte.DEFAULT;
        NULL_FREE_ATOMIC_ARRAY_0[0] = OneByte.DEFAULT;
        NULLABLE_ARRAY_0[0] = null;
        NULLABLE_ATOMIC_ARRAY_0[0] = null;

        TwoBytes val1 = new TwoBytes((byte)i, (byte)i);
        Asserts.assertEQ(NULL_FREE_ARRAY_1[0], TwoBytes.DEFAULT);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_1[0], TwoBytes.DEFAULT);
        Asserts.assertEQ(NULLABLE_ARRAY_1[0], null);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_1[0], null);

        try {
            NULL_FREE_ARRAY_1[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            NULL_FREE_ATOMIC_ARRAY_1[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }

        NULL_FREE_ARRAY_1[0] = val1;
        NULL_FREE_ATOMIC_ARRAY_1[0] = val1;
        NULLABLE_ARRAY_1[0] = val1;
        NULLABLE_ATOMIC_ARRAY_1[0] = val1;

        Asserts.assertEQ(NULL_FREE_ARRAY_1[0], val1);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_1[0], val1);
        Asserts.assertEQ(NULLABLE_ARRAY_1[0], val1);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_1[0], val1);

        NULL_FREE_ARRAY_1[0] = TwoBytes.DEFAULT;
        NULL_FREE_ATOMIC_ARRAY_1[0] = TwoBytes.DEFAULT;
        NULLABLE_ARRAY_1[0] = null;
        NULLABLE_ATOMIC_ARRAY_1[0] = null;

        TwoShorts val2 = new TwoShorts((short)i, (short)i);
        Asserts.assertEQ(NULL_FREE_ARRAY_2[0], TwoShorts.DEFAULT);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_2[0], TwoShorts.DEFAULT);
        Asserts.assertEQ(NULLABLE_ARRAY_2[0], null);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_2[0], null);

        try {
            NULL_FREE_ARRAY_2[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            NULL_FREE_ATOMIC_ARRAY_2[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }

        NULL_FREE_ARRAY_2[0] = val2;
        NULL_FREE_ATOMIC_ARRAY_2[0] = val2;
        NULLABLE_ARRAY_2[0] = val2;
        NULLABLE_ATOMIC_ARRAY_2[0] = val2;

        Asserts.assertEQ(NULL_FREE_ARRAY_2[0], val2);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_2[0], val2);
        Asserts.assertEQ(NULLABLE_ARRAY_2[0], val2);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_2[0], val2);

        NULL_FREE_ARRAY_2[0] = TwoShorts.DEFAULT;
        NULL_FREE_ATOMIC_ARRAY_2[0] = TwoShorts.DEFAULT;
        NULLABLE_ARRAY_2[0] = null;
        NULLABLE_ATOMIC_ARRAY_2[0] = null;

        TwoInts val3 = new TwoInts(i, i);
        Asserts.assertEQ(NULL_FREE_ARRAY_3[0], TwoInts.DEFAULT);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_3[0], TwoInts.DEFAULT);
        Asserts.assertEQ(NULLABLE_ARRAY_3[0], null);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_3[0], null);

        try {
            NULL_FREE_ARRAY_3[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            NULL_FREE_ATOMIC_ARRAY_3[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }

        NULL_FREE_ARRAY_3[0] = val3;
        NULL_FREE_ATOMIC_ARRAY_3[0] = val3;
        NULLABLE_ARRAY_3[0] = val3;
        NULLABLE_ATOMIC_ARRAY_3[0] = val3;

        Asserts.assertEQ(NULL_FREE_ARRAY_3[0], val3);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_3[0], val3);
        Asserts.assertEQ(NULLABLE_ARRAY_3[0], val3);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_3[0], val3);

        NULL_FREE_ARRAY_3[0] = TwoInts.DEFAULT;
        NULL_FREE_ATOMIC_ARRAY_3[0] = TwoInts.DEFAULT;
        NULLABLE_ARRAY_3[0] = null;
        NULLABLE_ATOMIC_ARRAY_3[0] = null;

        TwoLongs val4 = new TwoLongs(i, i);
        Asserts.assertEQ(NULL_FREE_ARRAY_4[0], TwoLongs.DEFAULT);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_4[0], TwoLongs.DEFAULT);
        Asserts.assertEQ(NULLABLE_ARRAY_4[0], null);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_4[0], null);

        try {
            NULL_FREE_ARRAY_4[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            NULL_FREE_ATOMIC_ARRAY_4[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }

        NULL_FREE_ARRAY_4[0] = val4;
        NULL_FREE_ATOMIC_ARRAY_4[0] = val4;
        NULLABLE_ARRAY_4[0] = val4;
        NULLABLE_ATOMIC_ARRAY_4[0] = val4;

        Asserts.assertEQ(NULL_FREE_ARRAY_4[0], val4);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_4[0], val4);
        Asserts.assertEQ(NULLABLE_ARRAY_4[0], val4);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_4[0], val4);

        NULL_FREE_ARRAY_4[0] = TwoLongs.DEFAULT;
        NULL_FREE_ATOMIC_ARRAY_4[0] = TwoLongs.DEFAULT;
        NULLABLE_ARRAY_4[0] = null;
        NULLABLE_ATOMIC_ARRAY_4[0] = null;

        ByteAndOop val5 = new ByteAndOop((byte)i, new MyClass(i));
        Asserts.assertEQ(NULL_FREE_ARRAY_5[0], ByteAndOop.DEFAULT);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_5[0], ByteAndOop.DEFAULT);
        Asserts.assertEQ(NULLABLE_ARRAY_5[0], null);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_5[0], null);

        try {
            NULL_FREE_ARRAY_5[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            NULL_FREE_ATOMIC_ARRAY_5[0] = null;
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }

        NULL_FREE_ARRAY_5[0] = val5;
        NULL_FREE_ATOMIC_ARRAY_5[0] = val5;
        NULLABLE_ARRAY_5[0] = val5;
        NULLABLE_ATOMIC_ARRAY_5[0] = val5;

        Asserts.assertEQ(NULL_FREE_ARRAY_5[0], val5);
        Asserts.assertEQ(NULL_FREE_ATOMIC_ARRAY_5[0], val5);
        Asserts.assertEQ(NULLABLE_ARRAY_5[0], val5);
        Asserts.assertEQ(NULLABLE_ATOMIC_ARRAY_5[0], val5);

        NULL_FREE_ARRAY_5[0] = ByteAndOop.DEFAULT;
        NULL_FREE_ATOMIC_ARRAY_5[0] = ByteAndOop.DEFAULT;
        NULLABLE_ARRAY_5[0] = null;
        NULLABLE_ATOMIC_ARRAY_5[0] = null;
    }

    // Test correct wiring of memory for flat accesses
    public static OneByte testMemoryEffects0() {
        NULLABLE_ARRAY_0[1] = CANARY0;
        NULLABLE_ATOMIC_ARRAY_0[1] = CANARY0;
        return NULLABLE_ARRAY_0[1];
    }

    public static TwoBytes testMemoryEffects1() {
        NULLABLE_ARRAY_1[1] = CANARY1;
        NULLABLE_ATOMIC_ARRAY_1[1] = CANARY1;
        return NULLABLE_ARRAY_1[1];
    }

    public static TwoShorts testMemoryEffects2() {
        NULLABLE_ARRAY_2[1] = CANARY2;
        NULLABLE_ATOMIC_ARRAY_2[1] = CANARY2;
        return NULLABLE_ARRAY_2[1];
    }

    public static void main(String[] args) {
        OneByte[] nullFreeArray0 = (OneByte[])ValueClass.newNullRestrictedNonAtomicArray(OneByte.class, 3, OneByte.DEFAULT);
        OneByte[] nullFreeAtomicArray0 = (OneByte[])ValueClass.newNullRestrictedAtomicArray(OneByte.class, 3, OneByte.DEFAULT);
        OneByte[] nullableArray0 = new OneByte[3];
        OneByte[] nullableAtomicArray0 = (OneByte[])ValueClass.newNullableAtomicArray(OneByte.class, 3);

        TwoBytes[] nullFreeArray1 = (TwoBytes[])ValueClass.newNullRestrictedNonAtomicArray(TwoBytes.class, 3, TwoBytes.DEFAULT);
        TwoBytes[] nullFreeAtomicArray1 = (TwoBytes[])ValueClass.newNullRestrictedAtomicArray(TwoBytes.class, 3, TwoBytes.DEFAULT);
        TwoBytes[] nullableArray1 = new TwoBytes[3];
        TwoBytes[] nullableAtomicArray1 = (TwoBytes[])ValueClass.newNullableAtomicArray(TwoBytes.class, 3);

        TwoShorts[] nullFreeArray2 = (TwoShorts[])ValueClass.newNullRestrictedNonAtomicArray(TwoShorts.class, 3, TwoShorts.DEFAULT);
        TwoShorts[] nullFreeAtomicArray2 = (TwoShorts[])ValueClass.newNullRestrictedAtomicArray(TwoShorts.class, 3, TwoShorts.DEFAULT);
        TwoShorts[] nullableArray2 = new TwoShorts[3];
        TwoShorts[] nullableAtomicArray2 = (TwoShorts[])ValueClass.newNullableAtomicArray(TwoShorts.class, 3);

        TwoInts[] nullFreeArray3 = (TwoInts[])ValueClass.newNullRestrictedNonAtomicArray(TwoInts.class, 3, TwoInts.DEFAULT);
        TwoInts[] nullFreeAtomicArray3 = (TwoInts[])ValueClass.newNullRestrictedAtomicArray(TwoInts.class, 3, TwoInts.DEFAULT);
        TwoInts[] nullableArray3 = new TwoInts[3];
        TwoInts[] nullableAtomicArray3 = (TwoInts[])ValueClass.newNullableAtomicArray(TwoInts.class, 3);

        TwoLongs[] nullFreeArray4 = (TwoLongs[])ValueClass.newNullRestrictedNonAtomicArray(TwoLongs.class, 3, TwoLongs.DEFAULT);
        TwoLongs[] nullFreeAtomicArray4 = (TwoLongs[])ValueClass.newNullRestrictedAtomicArray(TwoLongs.class, 3, TwoLongs.DEFAULT);
        TwoLongs[] nullableArray4 = new TwoLongs[3];
        TwoLongs[] nullableAtomicArray4 = (TwoLongs[])ValueClass.newNullableAtomicArray(TwoLongs.class, 3);

        ByteAndOop[] nullFreeArray5 = (ByteAndOop[])ValueClass.newNullRestrictedNonAtomicArray(ByteAndOop.class, 3, ByteAndOop.DEFAULT);
        ByteAndOop[] nullFreeAtomicArray5 = (ByteAndOop[])ValueClass.newNullRestrictedAtomicArray(ByteAndOop.class, 3, ByteAndOop.DEFAULT);
        ByteAndOop[] nullableArray5 = new ByteAndOop[3];
        ByteAndOop[] nullableAtomicArray5 = (ByteAndOop[])ValueClass.newNullableAtomicArray(ByteAndOop.class, 3);

        IntAndArrayOop[] nullFreeArray6 = (IntAndArrayOop[])ValueClass.newNullRestrictedNonAtomicArray(IntAndArrayOop.class, 3, IntAndArrayOop.DEFAULT);
        IntAndArrayOop[] nullFreeAtomicArray6 = (IntAndArrayOop[])ValueClass.newNullRestrictedAtomicArray(IntAndArrayOop.class, 3, IntAndArrayOop.DEFAULT);
        IntAndArrayOop[] nullableArray6 = new IntAndArrayOop[3];
        IntAndArrayOop[] nullableAtomicArray6 = (IntAndArrayOop[])ValueClass.newNullableAtomicArray(IntAndArrayOop.class, 3);

        MyValueEmpty[] nullFreeArrayEmpty = (MyValueEmpty[])ValueClass.newNullRestrictedNonAtomicArray(MyValueEmpty.class, 3, MyValueEmpty.DEFAULT);
        MyValueEmpty[] nullFreeAtomicArrayEmpty = (MyValueEmpty[])ValueClass.newNullRestrictedAtomicArray(MyValueEmpty.class, 3, MyValueEmpty.DEFAULT);
        MyValueEmpty[] nullableArrayEmpty = new MyValueEmpty[3];
        MyValueEmpty[] nullableAtomicArrayEmpty = (MyValueEmpty[])ValueClass.newNullableAtomicArray(MyValueEmpty.class, 3);

        // Write canary values to detect out of bound writes
        nullFreeArray0[0] = CANARY0;
        nullFreeArray0[2] = CANARY0;
        nullFreeAtomicArray0[0] = CANARY0;
        nullFreeAtomicArray0[2] = CANARY0;
        nullableArray0[0] = CANARY0;
        nullableArray0[2] = CANARY0;
        nullableAtomicArray0[0] = CANARY0;
        nullableAtomicArray0[2] = CANARY0;

        nullFreeArray1[0] = CANARY1;
        nullFreeArray1[2] = CANARY1;
        nullFreeAtomicArray1[0] = CANARY1;
        nullFreeAtomicArray1[2] = CANARY1;
        nullableArray1[0] = CANARY1;
        nullableArray1[2] = CANARY1;
        nullableAtomicArray1[0] = CANARY1;
        nullableAtomicArray1[2] = CANARY1;

        nullFreeArray2[0] = CANARY2;
        nullFreeArray2[2] = CANARY2;
        nullFreeAtomicArray2[0] = CANARY2;
        nullFreeAtomicArray2[2] = CANARY2;
        nullableArray2[0] = CANARY2;
        nullableArray2[2] = CANARY2;
        nullableAtomicArray2[0] = CANARY2;
        nullableAtomicArray2[2] = CANARY2;

        nullFreeArray3[0] = CANARY3;
        nullFreeArray3[2] = CANARY3;
        nullFreeAtomicArray3[0] = CANARY3;
        nullFreeAtomicArray3[2] = CANARY3;
        nullableArray3[0] = CANARY3;
        nullableArray3[2] = CANARY3;
        nullableAtomicArray3[0] = CANARY3;
        nullableAtomicArray3[2] = CANARY3;

        nullFreeArray4[0] = CANARY4;
        nullFreeArray4[2] = CANARY4;
        nullFreeAtomicArray4[0] = CANARY4;
        nullFreeAtomicArray4[2] = CANARY4;
        nullableArray4[0] = CANARY4;
        nullableArray4[2] = CANARY4;
        nullableAtomicArray4[0] = CANARY4;
        nullableAtomicArray4[2] = CANARY4;

        nullFreeArray5[0] = CANARY5;
        nullFreeArray5[2] = CANARY5;
        nullFreeAtomicArray5[0] = CANARY5;
        nullFreeAtomicArray5[2] = CANARY5;
        nullableArray5[0] = CANARY5;
        nullableArray5[2] = CANARY5;
        nullableAtomicArray5[0] = CANARY5;
        nullableAtomicArray5[2] = CANARY5;

        final int LIMIT = 50_000;
        for (int i = -50_000; i < LIMIT; ++i) {
            OneByte val0 = new OneByte((byte)i);
            TwoBytes val1 = new TwoBytes((byte)i, (byte)(i + 1));
            TwoShorts val2 = new TwoShorts((short)i, (short)(i + 1));
            TwoInts val3 = new TwoInts(i, i + 1);
            TwoLongs val4 = new TwoLongs(i, i + 1);

            testWrite0(nullFreeArray0, 1, val0);
            Asserts.assertEQ(testRead0(nullFreeArray0, 1), val0);
            checkCanary0(nullFreeArray0);

            testWrite0(nullFreeAtomicArray0, 1, val0);
            Asserts.assertEQ(testRead0(nullFreeAtomicArray0, 1), val0);
            checkCanary0(nullFreeAtomicArray0);

            testWrite0(nullableArray0, 1, val0);
            Asserts.assertEQ(testRead0(nullableArray0, 1), val0);
            checkCanary0(nullableArray0);
            testWrite0(nullableArray0, 1, null);
            Asserts.assertEQ(testRead0(nullableArray0, 1), null);
            checkCanary0(nullableArray0);

            testWrite0(nullableAtomicArray0, 1, val0);
            Asserts.assertEQ(testRead0(nullableAtomicArray0, 1), val0);
            checkCanary0(nullableAtomicArray0);
            testWrite0(nullableAtomicArray0, 1, null);
            Asserts.assertEQ(testRead0(nullableAtomicArray0, 1), null);
            checkCanary0(nullableAtomicArray0);

            testWrite1(nullFreeArray1, 1, val1);
            Asserts.assertEQ(testRead1(nullFreeArray1, 1), val1);
            checkCanary1(nullFreeArray1);

            testWrite1(nullFreeAtomicArray1, 1, val1);
            Asserts.assertEQ(testRead1(nullFreeAtomicArray1, 1), val1);
            checkCanary1(nullFreeAtomicArray1);

            testWrite1(nullableArray1, 1, val1);
            Asserts.assertEQ(testRead1(nullableArray1, 1), val1);
            checkCanary1(nullableArray1);
            testWrite1(nullableArray1, 1, null);
            Asserts.assertEQ(testRead1(nullableArray1, 1), null);
            checkCanary1(nullableArray1);

            testWrite1(nullableAtomicArray1, 1, val1);
            Asserts.assertEQ(testRead1(nullableAtomicArray1, 1), val1);
            checkCanary1(nullableAtomicArray1);
            testWrite1(nullableAtomicArray1, 1, null);
            Asserts.assertEQ(testRead1(nullableAtomicArray1, 1), null);
            checkCanary1(nullableAtomicArray1);

            testWrite2(nullFreeArray2, 1, val2);
            Asserts.assertEQ(testRead2(nullFreeArray2, 1), val2);
            checkCanary2(nullFreeArray2);

            testWrite2(nullFreeAtomicArray2, 1, val2);
            Asserts.assertEQ(testRead2(nullFreeAtomicArray2, 1), val2);
            checkCanary2(nullFreeAtomicArray2);

            testWrite2(nullableArray2, 1, val2);
            Asserts.assertEQ(testRead2(nullableArray2, 1), val2);
            checkCanary2(nullableArray2);
            testWrite2(nullableArray2, 1, null);
            Asserts.assertEQ(testRead2(nullableArray2, 1), null);
            checkCanary2(nullableArray2);

            testWrite2(nullableAtomicArray2, 1, val2);
            Asserts.assertEQ(testRead2(nullableAtomicArray2, 1), val2);
            checkCanary2(nullableAtomicArray2);
            testWrite2(nullableAtomicArray2, 1, null);
            Asserts.assertEQ(testRead2(nullableAtomicArray2, 1), null);
            checkCanary2(nullableAtomicArray2);

            testWrite3(nullFreeArray3, 1, val3);
            Asserts.assertEQ(testRead3(nullFreeArray3, 1), val3);
            checkCanary3(nullFreeArray3);

            testWrite3(nullFreeAtomicArray3, 1, val3);
            Asserts.assertEQ(testRead3(nullFreeAtomicArray3, 1), val3);
            checkCanary3(nullFreeAtomicArray3);

            testWrite3(nullableArray3, 1, val3);
            Asserts.assertEQ(testRead3(nullableArray3, 1), val3);
            checkCanary3(nullableArray3);
            testWrite3(nullableArray3, 1, null);
            Asserts.assertEQ(testRead3(nullableArray3, 1), null);
            checkCanary3(nullableArray3);

            testWrite3(nullableAtomicArray3, 1, val3);
            Asserts.assertEQ(testRead3(nullableAtomicArray3, 1), val3);
            checkCanary3(nullableAtomicArray3);
            testWrite3(nullableAtomicArray3, 1, null);
            Asserts.assertEQ(testRead3(nullableAtomicArray3, 1), null);
            checkCanary3(nullableAtomicArray3);

            testWrite4(nullFreeArray4, 1, val4);
            Asserts.assertEQ(testRead4(nullFreeArray4, 1), val4);
            checkCanary4(nullFreeArray4);

            testWrite4(nullFreeAtomicArray4, 1, val4);
            Asserts.assertEQ(testRead4(nullFreeAtomicArray4, 1), val4);
            checkCanary4(nullFreeAtomicArray4);

            testWrite4(nullableArray4, 1, val4);
            Asserts.assertEQ(testRead4(nullableArray4, 1), val4);
            checkCanary4(nullableArray4);
            testWrite4(nullableArray4, 1, null);
            Asserts.assertEQ(testRead4(nullableArray4, 1), null);
            checkCanary4(nullableArray4);

            testWrite4(nullableAtomicArray4, 1, val4);
            Asserts.assertEQ(testRead4(nullableAtomicArray4, 1), val4);
            checkCanary4(nullableAtomicArray4);
            testWrite4(nullableAtomicArray4, 1, null);
            Asserts.assertEQ(testRead4(nullableAtomicArray4, 1), null);
            checkCanary4(nullableAtomicArray4);

            ByteAndOop val5 = new ByteAndOop((byte)i, new MyClass(i));
            testWrite5(nullFreeArray5, 1, val5);
            testWrite5(nullFreeAtomicArray5, 1, val5);
            testWrite5(nullableArray5, 1, val5);
            testWrite5(nullableAtomicArray5, 1, val5);

            IntAndArrayOop val6 = new IntAndArrayOop(i, new MyClass[1]);
            nullFreeArray6[1] = val6;
            nullFreeArray6[2] = nullFreeArray6[1];
            nullFreeAtomicArray6[1] = val6;
            nullFreeAtomicArray6[2] = nullFreeAtomicArray6[1];
            nullableArray6[1] = val6;
            nullableArray6[2] = nullableArray6[1];
            nullableAtomicArray6[1] = val6;
            nullableAtomicArray6[2] = nullableAtomicArray6[1];
            Asserts.assertEQ(nullFreeArray6[0], new IntAndArrayOop(0, null));
            Asserts.assertEQ(nullFreeAtomicArray6[0], new IntAndArrayOop(0, null));
            Asserts.assertEQ(nullableArray6[0], null);
            Asserts.assertEQ(nullableAtomicArray6[0], null);
            Asserts.assertEQ(nullFreeArray6[1], val6);
            Asserts.assertEQ(nullFreeAtomicArray6[1], val6);
            Asserts.assertEQ(nullableArray6[1], val6);
            Asserts.assertEQ(nullableAtomicArray6[1], val6);
            Asserts.assertEQ(nullFreeArray6[2], val6);
            Asserts.assertEQ(nullFreeAtomicArray6[2], val6);
            Asserts.assertEQ(nullableArray6[2], val6);
            Asserts.assertEQ(nullableAtomicArray6[2], val6);

            // Test empty arrays
            MyValueEmpty valEmpty = new MyValueEmpty();
            nullFreeArrayEmpty[1] = valEmpty;
            nullFreeArrayEmpty[2] = nullFreeArrayEmpty[1];
            nullFreeAtomicArrayEmpty[1] = valEmpty;
            nullFreeAtomicArrayEmpty[2] = nullFreeAtomicArrayEmpty[1];
            nullableArrayEmpty[1] = valEmpty;
            nullableArrayEmpty[2] = nullableArrayEmpty[1];
            nullableAtomicArrayEmpty[1] = valEmpty;
            nullableAtomicArrayEmpty[2] = nullableAtomicArrayEmpty[1];
            Asserts.assertEQ(nullFreeArrayEmpty[0], new MyValueEmpty());
            Asserts.assertEQ(nullFreeAtomicArrayEmpty[0], new MyValueEmpty());
            Asserts.assertEQ(nullableArrayEmpty[0], null);
            Asserts.assertEQ(nullableAtomicArrayEmpty[0], null);
            Asserts.assertEQ(nullFreeArrayEmpty[1], valEmpty);
            Asserts.assertEQ(nullFreeAtomicArrayEmpty[1], valEmpty);
            Asserts.assertEQ(nullableArrayEmpty[1], valEmpty);
            Asserts.assertEQ(nullableAtomicArrayEmpty[1], valEmpty);
            Asserts.assertEQ(nullFreeArrayEmpty[2], valEmpty);
            Asserts.assertEQ(nullFreeAtomicArrayEmpty[2], valEmpty);
            Asserts.assertEQ(nullableArrayEmpty[2], valEmpty);
            Asserts.assertEQ(nullableAtomicArrayEmpty[2], valEmpty);

            if (i > (LIMIT - 50)) {
                // After warmup, produce some garbage to trigger GC
                produceGarbage();
            }

            Asserts.assertEQ(testRead5(nullFreeArray5, 1), val5);
            checkCanary5(nullFreeArray5);

            Asserts.assertEQ(testRead5(nullFreeAtomicArray5, 1), val5);
            checkCanary5(nullFreeAtomicArray5);

            Asserts.assertEQ(testRead5(nullableArray5, 1), val5);
            checkCanary5(nullableArray5);

            testWrite5(nullableArray5, 1, null);
            Asserts.assertEQ(testRead5(nullableArray5, 1), null);
            checkCanary5(nullableArray5);

            Asserts.assertEQ(testRead5(nullableAtomicArray5, 1), val5);
            checkCanary5(nullableAtomicArray5);

            testWrite5(nullableAtomicArray5, 1, null);
            Asserts.assertEQ(testRead5(nullableAtomicArray5, 1), null);
            checkCanary5(nullableAtomicArray5);

            // Test intrinsics
            TwoBytes[] res = testNullRestrictedArrayIntrinsic(3, 1, val1);
            Asserts.assertEQ(testRead1(res, 1), val1);
            res = testNullRestrictedArrayIntrinsicDynamic1(3, 1, val1);
            Asserts.assertEQ(testRead1(res, 1), val1);
            res = testNullRestrictedArrayIntrinsicDynamic2(3, 1, val1);
            Asserts.assertEQ(testRead1(res, 1), val1);
            res = testNullRestrictedArrayIntrinsicDynamic3(3, 1, val1);
            Asserts.assertEQ(testRead1(res, 1), val1);
            res = testNullRestrictedAtomicArrayIntrinsic(3, 1, val1);
            Asserts.assertEQ(testRead1(res, 1), val1);
            res = testNullRestrictedAtomicArrayIntrinsicDynamic1(3, 1, val1);
            Asserts.assertEQ(testRead1(res, 1), val1);
            res = testNullRestrictedAtomicArrayIntrinsicDynamic2(3, 1, val1);
            Asserts.assertEQ(testRead1(res, 1), val1);
            res = testNullRestrictedAtomicArrayIntrinsicDynamic3(3, 1, val1);
            Asserts.assertEQ(testRead1(res, 1), val1);
            res = testNullableAtomicArrayIntrinsic(3, 1, val1);
            Asserts.assertEQ(testRead1(res, 1), val1);
            res = testNullableAtomicArrayIntrinsic(3, 2, null);
            Asserts.assertEQ(testRead1(res, 2), null);

            testProfiling();

            // Pollute profile
            Object[] objArray = new Object[1];
            testWrite6(objArray, 0, objArray);
            Asserts.assertEQ(testRead6(objArray, 0), objArray);

            testWrite6(nullFreeArray1, 1, val1);
            Asserts.assertEQ(testRead6(nullFreeArray1, 1), val1);
            checkCanary1(nullFreeArray1);

            testWrite6(nullFreeAtomicArray1, 1, val1);
            Asserts.assertEQ(testRead6(nullFreeAtomicArray1, 1), val1);
            checkCanary1(nullFreeAtomicArray1);

            testWrite6(nullableArray1, 1, val1);
            Asserts.assertEQ(testRead6(nullableArray1, 1), val1);
            checkCanary1(nullableArray1);
            testWrite6(nullableArray1, 1, null);
            Asserts.assertEQ(testRead6(nullableArray1, 1), null);
            checkCanary1(nullableArray1);

            testWrite6(nullableAtomicArray1, 1, val1);
            Asserts.assertEQ(testRead6(nullableAtomicArray1, 1), val1);
            checkCanary1(nullableAtomicArray1);
            testWrite6(nullableAtomicArray1, 1, null);
            Asserts.assertEQ(testRead6(nullableAtomicArray1, 1), null);
            checkCanary1(nullableAtomicArray1);

            // Test scalar replacement of array allocations
            testScalarReplacement1(val0, null, false);
            testScalarReplacement2(val0, null, false);
            testScalarReplacement3(val0, null, false);
            testScalarReplacement4(val0, null, false);
            testScalarReplacement5(val1, false);
            testScalarReplacement6(val1, false);
            testScalarReplacement7(val1, false);
            testScalarReplacement8(val1, false);

            // Test access to constant arrays
            testConstantArrays(i);

            // Test correct wiring of memory for flat accesses
            Asserts.assertEQ(testMemoryEffects0(), CANARY0);
            Asserts.assertEQ(testMemoryEffects1(), CANARY1);
            Asserts.assertEQ(testMemoryEffects2(), CANARY2);
        }

        try {
            testWrite0(nullFreeArray0, 0, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary0(nullFreeArray0);
        try {
            testWrite0(nullFreeAtomicArray0, 0, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary0(nullFreeAtomicArray0);

        try {
            testWrite1(nullFreeArray1, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary1(nullFreeArray1);
        try {
            testWrite1(nullFreeAtomicArray1, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary1(nullFreeAtomicArray1);

        try {
            testWrite2(nullFreeArray2, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary2(nullFreeArray2);
        try {
            testWrite2(nullFreeAtomicArray2, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary2(nullFreeAtomicArray2);

        try {
            testWrite3(nullFreeArray3, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary3(nullFreeArray3);
        try {
            testWrite3(nullFreeAtomicArray3, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary3(nullFreeAtomicArray3);

        try {
            testWrite4(nullFreeArray4, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary4(nullFreeArray4);
        try {
            testWrite4(nullFreeAtomicArray4, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary4(nullFreeAtomicArray4);

        try {
            testWrite5(nullFreeArray5, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary5(nullFreeArray5);
        try {
            testWrite5(nullFreeAtomicArray5, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        checkCanary5(nullFreeAtomicArray5);

        // Test intrinsics
        try {
            testNullRestrictedArrayIntrinsic(3, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            testNullRestrictedArrayIntrinsicDynamic1(3, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            testNullRestrictedArrayIntrinsicDynamic2(3, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            testNullRestrictedArrayIntrinsicDynamic3(3, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            testNullRestrictedAtomicArrayIntrinsic(3, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            testNullRestrictedAtomicArrayIntrinsicDynamic1(3, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            testNullRestrictedAtomicArrayIntrinsicDynamic2(3, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            testNullRestrictedAtomicArrayIntrinsicDynamic3(3, 1, null);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }

        testScalarReplacement1(CANARY0, null, true);
        testScalarReplacement2(CANARY0, null, true);
        testScalarReplacement3(CANARY0, null, true);
        testScalarReplacement4(CANARY0, null, true);
        testScalarReplacement5(CANARY1, true);
        testScalarReplacement6(CANARY1, true);
        testScalarReplacement7(CANARY1, true);
        testScalarReplacement8(CANARY1, true);
    }
}

