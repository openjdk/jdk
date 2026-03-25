/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import jdk.test.lib.Asserts;
import jdk.test.lib.helpers.StrictInit;

/*
 * @test id=noFlags
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/timeout=300 compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=no-flattening
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:-UseNullableValueFlattening -XX:-UseAtomicValueFlattening -XX:-UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=nAVF
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:-UseNullableValueFlattening -XX:-UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=AVF
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:-UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:-UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=AVF-nAVF
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:-UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=NVF
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:-UseAtomicValueFlattening -XX:-UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=NVF-nAVF
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:-UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=NVF-AVF
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:-UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=all-flattening
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=all-flattening-di
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               -XX:CompileCommand=dontinline,*::testHelper*
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=all-flattening-pass-fields
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               -XX:+InlineTypeReturnedAsFields -XX:+InlineTypePassFieldsAsArgs
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=all-flattening-no-pass-fields
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               -XX:-InlineTypeReturnedAsFields -XX:-InlineTypePassFieldsAsArgs
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=all-flattening-return-fields
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               -XX:+InlineTypeReturnedAsFields -XX:-InlineTypePassFieldsAsArgs
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

/*
 * @test id=all-flattening-pass-args
 * @key randomness
 * @summary Test support for null markers in flat fields.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile TestFieldNullMarkers.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestFieldNullMarkers$StrictFieldHolder
 * @run main/othervm/timeout=300 -Xbatch
 *                               -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                               -XX:-InlineTypeReturnedAsFields -XX:+InlineTypePassFieldsAsArgs
 *                               compiler.valhalla.inlinetypes.TestFieldNullMarkers
 */

public class TestFieldNullMarkers {

    public TestFieldNullMarkers() {
        field12 = new MyValue8((byte)0);
        field13 = MyValue14.DEFAULT;
        field16 = MyValue14.DEFAULT;
        field17 = new MyValue15(null);
        field19 = new MyValue16(null, null);
        field20 = new MyValue17(null, (byte)0, (byte)0);
        emptyField1 = new MyValueEmpty();
        emptyField2 = new MyValueEmpty();
        super();
    }

    // Value class with two nullable flat fields
    @LooselyConsistentValue
    static value class MyValue1 {
        byte x;
        MyValue2 val1;
        MyValue2 val2;

        public MyValue1(byte x, MyValue2 val1, MyValue2 val2) {
            this.x = x;
            this.val1 = val1;
            this.val2 = val2;
        }

        public String toString() {
            return "x = " + x + ", val1 = [" + val1 + "], val2 = [" + val2 + "]";
        }
    }

    @LooselyConsistentValue
    static abstract value class MyAbstract1 {
        byte x;

        public MyAbstract1(byte x) {
            this.x = x;
        }
    }

    // Empty value class inheriting single field from abstract super class
    @LooselyConsistentValue
    static value class MyValue2 extends MyAbstract1 {
        public MyValue2(byte x) {
            super(x);
        }

        public String toString() {
            return "x = " + x;
        }
    }

    // Value class with a hole in the payload that will be used for the null marker
    @LooselyConsistentValue
    static value class MyValue3 {
        byte x;
        // Hole that will be used by the null marker
        int i;

        public MyValue3(byte x) {
            this.x = x;
            this.i = x;
        }

        public String toString() {
            return "x = " + x + ", i = " + i;
        }
    }

    // Value class with two nullable flat fields that have their null markers *not* at the end of the payload
    @LooselyConsistentValue
    static value class MyValue4 {
        MyValue3 val1;
        MyValue3 val2;

        public MyValue4(MyValue3 val1, MyValue3 val2) {
            this.val1 = val1;
            this.val2 = val2;
        }

        public String toString() {
            return "val1 = [" + val1 + "], val2 = [" + val2 + "]";
        }
    }

    @LooselyConsistentValue
    static value class MyValue5C {
        byte x;

        public MyValue5C(byte x) {
            this.x = x;
        }
    }

    @LooselyConsistentValue
    static value class MyValue5B {
        byte x;
        MyValue5C val;

        public MyValue5B(byte x, MyValue5C val) {
            this.x = x;
            this.val = val;
        }
    }

    @LooselyConsistentValue
    static value class MyValue5A {
        byte x;
        MyValue5B val;

        public MyValue5A(byte x, MyValue5B val) {
            this.x = x;
            this.val = val;
        }
    }

    // Value class with deep nesting of nullable flat fields
    @LooselyConsistentValue
    static value class MyValue5 {
        byte x;
        MyValue5A val;

        public MyValue5(byte x, MyValue5A val) {
            this.x = x;
            this.val = val;
        }
    }

    @LooselyConsistentValue
    static value class MyValueEmpty {

    }

    // Value class with flat field of empty value class
    @LooselyConsistentValue
    static value class MyValue6 {
        MyValueEmpty val;

        public MyValue6(MyValueEmpty val) {
            this.val = val;
        }
    }

    // Same as MyValue6 but one more level of nested flat fields
    @LooselyConsistentValue
    static value class MyValue7 {
        MyValue6 val;

        public MyValue7(MyValue6 val) {
            this.val = val;
        }
    }

    // Some more field types

    @LooselyConsistentValue
    static value class MyValue8 {
        byte b;

        public MyValue8(byte b) {
            this.b = b;
        }
    }

    @LooselyConsistentValue
    static value class MyValue9 {
        short s;

        public MyValue9(short s) {
            this.s = s;
        }
    }

    @LooselyConsistentValue
    static value class MyValue10 {
        int i;

        public MyValue10(int i) {
            this.i = i;
        }
    }

    @LooselyConsistentValue
    static value class MyValue11 {
        float f;

        public MyValue11(float f) {
            this.f = f;
        }
    }

    @LooselyConsistentValue
    static value class MyValue12 {
        char c;

        public MyValue12(char c) {
            this.c = c;
        }
    }

    @LooselyConsistentValue
    static value class MyValue13 {
        boolean b;

        public MyValue13(boolean b) {
            this.b = b;
        }
    }

    // Test value class with nullable and null-free fields
    @LooselyConsistentValue
    static value class MyValue14 {
        @NullRestricted
        MyValue8 nullfree;
        MyValue8 nullable;

        public MyValue14(MyValue8 nullfree, MyValue8 nullable) {
            this.nullfree = nullfree;
            this.nullable = nullable;
        }

        public static final MyValue14 DEFAULT = new MyValue14(new MyValue8((byte)0), null);
    }

    static class MyClass {
        int x;

        public MyClass(int x) {
            this.x = x;
        }
    }

    // Value class with oop field
    @LooselyConsistentValue
    static value class MyValue15 {
        MyClass obj;

        public MyValue15(MyClass obj) {
            this.obj = obj;
        }
    }

    // Value class with two oop fields
    @LooselyConsistentValue
    static value class MyValue16 {
        MyClass obj1;
        MyClass obj2;

        public MyValue16(MyClass obj1, MyClass obj2) {
            this.obj1 = obj1;
            this.obj2 = obj2;
        }
    }

    // Value class with oop field and primitive fields
    @LooselyConsistentValue
    static value class MyValue17 {
        byte b1;
        MyClass obj;
        byte b2;

        public MyValue17(MyClass obj, byte b1, byte b2) {
            this.obj = obj;
            this.b1 = b1;
            this.b2 = b2;
        }
    }

    MyValue1 field1; // Not flat
    MyValue4 field2; // Not flat
    MyValue5 field3; // Flat
    MyValue6 field4; // Flat
    MyValue7 field5; // Flat
    MyValue8 field6; // Flat
    MyValue9 field7; // Flat
    MyValue10 field8; // Flat
    MyValue11 field9; // Flat
    MyValue12 field10; // Flat
    MyValue13 field11; // Flat

    @NullRestricted
    volatile MyValue8 field12;

    @NullRestricted
    MyValue14 field13; // Null-free, flat
    volatile MyValue14 field14; // Nullable, atomic, flat
    MyValue14 field15;          // Nullable, (atomic), flat
    @NullRestricted
    volatile MyValue14 field16; // Null-free, atomic, flat

    @NullRestricted
    volatile MyValue15 field17;
    MyValue15 field18;
    @NullRestricted
    volatile MyValue16 field19;
    @NullRestricted
    volatile MyValue17 field20;
    MyValue17 field21;

    // Combinations of strict fields
    static class StrictFieldHolder {
        @StrictInit
        MyValue8 strictField1;
        @StrictInit
        final MyValue8 strictField2;
        @NullRestricted
        MyValue8 strictField3;
        @NullRestricted
        final MyValue8 strictField4;
        @StrictInit
        volatile MyValue8 strictField5;
        @NullRestricted
        volatile MyValue8 strictField6;

        @StrictInit
        TwoBytes strictField7;
        @StrictInit
        final TwoBytes strictField8;
        @NullRestricted
        TwoBytes strictField9;
        @NullRestricted
        final TwoBytes strictField10;
        @StrictInit
        volatile TwoBytes strictField11;
        @NullRestricted
        volatile TwoBytes strictField12;

        public StrictFieldHolder(MyValue8 val8, MyValue8 val8NullFree, TwoBytes twoBytes, TwoBytes twoBytesNullFree) {
            strictField1 = val8;
            strictField2 = val8;
            strictField3 = val8NullFree;
            strictField4 = val8NullFree;
            strictField5 = val8NullFree;
            strictField6 = val8NullFree;

            strictField7 = twoBytes;
            strictField8 = twoBytes;
            strictField9 = twoBytesNullFree;
            strictField10 = twoBytesNullFree;
            strictField11 = twoBytesNullFree;
            strictField12 = twoBytesNullFree;
            super();
        }
    }

    @NullRestricted
    MyValueEmpty emptyField1;
    @NullRestricted
    volatile MyValueEmpty emptyField2;
    MyValueEmpty emptyField3;
    volatile MyValueEmpty emptyField4;

    static final MyValue1 VAL1 = new MyValue1((byte)42, new MyValue2((byte)43), null);
    static final MyValue4 VAL4 = new MyValue4(new MyValue3((byte)42), null);
    static final MyValue5 VAL5 = new MyValue5((byte)42, new MyValue5A((byte)43, new MyValue5B((byte)44, new MyValue5C((byte)45))));
    static final MyValue6 VAL6 = new MyValue6(new MyValueEmpty());
    static final MyValue7 VAL7 = new MyValue7(new MyValue6(new MyValueEmpty()));

    // Using two bytes such that null-free fields will not be naturally atomic
    @LooselyConsistentValue
    static value class TwoBytes {
        byte b1;
        byte b2;

        public TwoBytes(byte b1, byte b2) {
            this.b1 = b1;
            this.b2 = b2;
        }

        public static final TwoBytes DEFAULT = new TwoBytes((byte)0, (byte)0);
    }

    static private final MyValue8 CANARY_VALUE = new MyValue8((byte)42);

    public static class Cage1 {
        MyValue8 canary1 = CANARY_VALUE;

        @NullRestricted
        volatile TwoBytes field;

        MyValue8 canary2 = CANARY_VALUE;

        public Cage1() {
            field = TwoBytes.DEFAULT;
            super();
        }

        public void verify(TwoBytes val) {
            Asserts.assertEQ(canary1, CANARY_VALUE);
            Asserts.assertEQ(field, val);
            Asserts.assertEQ(canary2, CANARY_VALUE);
        }
    }

    public static class Cage2 {
        @NullRestricted
        MyValue8 canary1;

        @NullRestricted
        volatile TwoBytes field;

        @NullRestricted
        MyValue8 canary2;

        public Cage2() {
            canary1 = CANARY_VALUE;
            field = TwoBytes.DEFAULT;
            canary2 = CANARY_VALUE;
            super();
        }

        public void verify(TwoBytes val) {
            Asserts.assertEQ(canary1, CANARY_VALUE);
            Asserts.assertEQ(field, val);
            Asserts.assertEQ(canary2, CANARY_VALUE);
        }
    }

    public static class Cage3 {
        @NullRestricted
        MyValue8 canary1;

        volatile TwoBytes field;

        @NullRestricted
        MyValue8 canary2;

        public Cage3() {
            canary1 = CANARY_VALUE;
            canary2 = CANARY_VALUE;
            super();
        }

        public void verify(TwoBytes val) {
            Asserts.assertEQ(canary1, CANARY_VALUE);
            Asserts.assertEQ(field, val);
            Asserts.assertEQ(canary2, CANARY_VALUE);
        }
    }

    public static class Cage4 {
        MyValue8 canary1 = CANARY_VALUE;

        volatile TwoBytes field;

        MyValue8 canary2 = CANARY_VALUE;

        public void verify(TwoBytes val) {
            Asserts.assertEQ(canary1, CANARY_VALUE);
            Asserts.assertEQ(field, val);
            Asserts.assertEQ(canary2, CANARY_VALUE);
        }
    }

    static final Cage1 canaryCage1 = new Cage1();
    static final Cage2 canaryCage2 = new Cage2();
    static final Cage3 canaryCage3 = new Cage3();
    static final Cage4 canaryCage4 = new Cage4();

    // Check that the canary values are not accidentally overwritten
    public void testOutOfBoundsAccess(int i) {
        TwoBytes val = new TwoBytes((byte)i, (byte)(i+1));
        canaryCage1.field = val;
        canaryCage1.verify(val);

        canaryCage2.field = val;
        canaryCage2.verify(val);

        canaryCage3.field = val;
        canaryCage3.verify(val);

        canaryCage3.field = null;
        canaryCage3.verify(null);

        canaryCage4.field = val;
        canaryCage4.verify(val);

        canaryCage4.field = null;
        canaryCage4.verify(null);
    }

    // Test that the calling convention is keeping track of the null marker
    public MyValue1 testHelper1(MyValue1 val) {
        return val;
    }

    public void testSet1(MyValue1 val) {
        field1 = testHelper1(val);
    }

    public MyValue1 testGet1() {
        return field1;
    }

    public void testDeopt1(byte x, MyValue1 neverNull, MyValue1 alwaysNull, boolean deopt) {
        MyValue2 val2 = new MyValue2(x);
        MyValue1 val1 = new MyValue1(x, val2, val2);
        if (deopt) {
            Asserts.assertEQ(val1.x, x);
            Asserts.assertEQ(val1.val1, val2);
            Asserts.assertEQ(val1.val2, val2);
            Asserts.assertEQ(neverNull.x, x);
            Asserts.assertEQ(neverNull.val1, val2);
            Asserts.assertEQ(neverNull.val2, val2);
            Asserts.assertEQ(alwaysNull.x, x);
            Asserts.assertEQ(alwaysNull.val1, null);
            Asserts.assertEQ(alwaysNull.val2, null);
        }
    }

    public void testOSR() {
        // Trigger OSR
        for (int i = 0; i < 100_000; ++i) {
            field1 = null;
            Asserts.assertEQ(field1, null);
            MyValue2 val2 = new MyValue2((byte)i);
            MyValue1 val = new MyValue1((byte)i, val2, null);
            field1 = val;
            Asserts.assertEQ(field1.x, (byte)i);
            Asserts.assertEQ(field1.val1, val2);
            Asserts.assertEQ(field1.val2, null);
        }
    }

    public boolean testACmp(MyValue2 val2) {
        return field1.val1 == val2;
    }

    // Test that the calling convention is keeping track of the null marker
    public MyValue4 testHelper2(MyValue4 val) {
        return val;
    }

    public void testSet2(MyValue4 val) {
        field2 = testHelper2(val);
    }

    public MyValue4 testGet2() {
        return field2;
    }

    public void testDeopt2(byte x, MyValue4 neverNull, MyValue4 alwaysNull, boolean deopt) {
        MyValue3 val3 = new MyValue3(x);
        MyValue4 val4 = new MyValue4(val3, null);
        if (deopt) {
            Asserts.assertEQ(val4.val1, val3);
            Asserts.assertEQ(val4.val2, null);
            Asserts.assertEQ(neverNull.val1, val3);
            Asserts.assertEQ(neverNull.val2, val3);
            Asserts.assertEQ(alwaysNull.val1, null);
            Asserts.assertEQ(alwaysNull.val2, null);
        }
    }

    // Test that the calling convention is keeping track of the null marker
    public MyValue5 testHelper3(MyValue5 val) {
        return val;
    }

    public void testSet3(MyValue5 val) {
        field3 = testHelper3(val);
    }

    public MyValue5 testGet3() {
        return field3;
    }

    public void testDeopt3(byte x, MyValue5 val6, MyValue5 val7, MyValue5 val8, MyValue5 val9, boolean deopt) {
        MyValue5 val1 = new MyValue5(x, new MyValue5A(x, new MyValue5B(x, new MyValue5C(x))));
        MyValue5 val2 = new MyValue5(x, new MyValue5A(x, new MyValue5B(x, null)));
        MyValue5 val3 = new MyValue5(x, new MyValue5A(x, null));
        MyValue5 val4 = new MyValue5(x, null);
        MyValue5 val5 = null;
        if (deopt) {
            Asserts.assertEQ(val1.x, x);
            Asserts.assertEQ(val1.val.x, x);
            Asserts.assertEQ(val1.val.val.x, x);
            Asserts.assertEQ(val1.val.val.val.x, x);
            Asserts.assertEQ(val2.x, x);
            Asserts.assertEQ(val2.val.x, x);
            Asserts.assertEQ(val2.val.val.x, x);
            Asserts.assertEQ(val2.val.val.val, null);
            Asserts.assertEQ(val3.x, x);
            Asserts.assertEQ(val3.val.x, x);
            Asserts.assertEQ(val3.val.val, null);
            Asserts.assertEQ(val4.x, x);
            Asserts.assertEQ(val4.val, null);
            Asserts.assertEQ(val5, null);

            Asserts.assertEQ(val6.x, x);
            Asserts.assertEQ(val6.val.x, x);
            Asserts.assertEQ(val6.val.val.x, x);
            Asserts.assertEQ(val6.val.val.val.x, x);
            Asserts.assertEQ(val7.x, x);
            Asserts.assertEQ(val7.val.x, x);
            Asserts.assertEQ(val7.val.val.x, x);
            Asserts.assertEQ(val7.val.val.val, null);
            Asserts.assertEQ(val8.x, x);
            Asserts.assertEQ(val8.val.x, x);
            Asserts.assertEQ(val8.val.val, null);
            Asserts.assertEQ(val9.x, x);
            Asserts.assertEQ(val9.val, null);
        }
    }

    // Test that the calling convention is keeping track of the null marker
    public MyValue6 testHelper4(MyValue6 val) {
        return val;
    }

    public void testSet4(MyValue6 val) {
        field4 = testHelper4(val);
    }

    public MyValue6 testGet4() {
        return field4;
    }

    public void testDeopt4(MyValue6 val4, MyValue6 val5, MyValue6 val6, boolean deopt) {
        MyValue6 val1 = new MyValue6(new MyValueEmpty());
        MyValue6 val2 = new MyValue6(null);
        MyValue6 val3 = null;
        if (deopt) {
            Asserts.assertEQ(val1.val, new MyValueEmpty());
            Asserts.assertEQ(val2.val, null);
            Asserts.assertEQ(val3, null);

            Asserts.assertEQ(val4.val, new MyValueEmpty());
            Asserts.assertEQ(val5.val, null);
            Asserts.assertEQ(val6, null);
        }
    }

    // Test that the calling convention is keeping track of the null marker
    public MyValue7 testHelper5(MyValue7 val) {
        return val;
    }

    public void testSet5(MyValue7 val) {
        field5 = testHelper5(val);
    }

    public MyValue7 testGet5() {
        return field5;
    }

    public void testDeopt5(MyValue7 val5, MyValue7 val6, MyValue7 val7, MyValue7 val8, boolean deopt) {
        MyValue7 val1 = new MyValue7(new MyValue6(new MyValueEmpty()));
        MyValue7 val2 = new MyValue7(new MyValue6(null));
        MyValue7 val3 = new MyValue7(null);
        MyValue7 val4 = null;
        if (deopt) {
            Asserts.assertEQ(val1.val, new MyValue6(new MyValueEmpty()));
            Asserts.assertEQ(val2.val, new MyValue6(null));
            Asserts.assertEQ(val3.val, null);
            Asserts.assertEQ(val4, null);

            Asserts.assertEQ(val5.val, new MyValue6(new MyValueEmpty()));
            Asserts.assertEQ(val6.val, new MyValue6(null));
            Asserts.assertEQ(val7.val, null);
            Asserts.assertEQ(val8, null);
        }
    }

    // Make sure that flat field accesses contain a (implicit) null check
    public static void testNPE1() {
        TestFieldNullMarkers t = null;
        try {
            MyValue8 v = t.field6;
            throw new RuntimeException("No NPE thrown!");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    public static void testNPE2() {
        TestFieldNullMarkers t = null;
        try {
            t.field6 = null;
            throw new RuntimeException("No NPE thrown!");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    public void checkFields(int i) {
        Asserts.assertEQ(field6.b, (byte)i);
        Asserts.assertEQ(field7.s, (short)i);
        Asserts.assertEQ(field8.i, i);
        Asserts.assertEQ(field9.f, (float)i);
        Asserts.assertEQ(field10.c, (char)i);
        Asserts.assertEQ(field11.b, (i % 2) == 0);
    }

    // Test that writing and reading a (signed) byte stays in bounds
    public void testBounds(int i) {
        MyValue8 val = new MyValue8((byte)i);
        field6 = val;
        int b = field6.b;
        if (b < -128 || b > 127) {
            throw new RuntimeException("Byte value out of bounds: " + b);
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

    // Test that barriers are emitted when writing flat, atomic fields with oops
    public void testWriteOopFields1(MyValue15 val) {
        field17 = val;
        field18 = val;
    }

    public void testWriteOopFields2(MyValue16 val) {
        field19 = val;
    }

    public void testWriteOopFields3(MyValue17 val) {
        field20 = val;
        field21 = val;
    }

    public static class MyHolderClass9 {
        @NullRestricted
        TwoBytes field1;

        TwoBytes field2;

        @NullRestricted
        volatile TwoBytes field3;

        volatile TwoBytes field4;

        public MyHolderClass9() {
            field1 = TwoBytes.DEFAULT;
            field3 = TwoBytes.DEFAULT;
            super();
        }
    }

    static final MyHolderClass9 constantHolder = new MyHolderClass9();

    // Test loading a flat field from a constant container (should not be constant folded because fields are immutable)
    public void testLoadingFromConstantHolder(int i) {
        TwoBytes val = new TwoBytes((byte)i, (byte)(i + 1));
        constantHolder.field1 = val;
        Asserts.assertEQ(constantHolder.field1, val);

        constantHolder.field2 = val;
        Asserts.assertEQ(constantHolder.field2, val);

        constantHolder.field2 = null;
        Asserts.assertEQ(constantHolder.field2, null);

        constantHolder.field3 = val;
        Asserts.assertEQ(constantHolder.field3, val);

        constantHolder.field4 = val;
        Asserts.assertEQ(constantHolder.field4, val);

        constantHolder.field4 = null;
        Asserts.assertEQ(constantHolder.field4, null);
    }

    public void testStrictFields(StrictFieldHolder holder, MyValue8 val8, MyValue8 val8NullFree, TwoBytes twoBytes, TwoBytes twoBytesNullFree) {
        Asserts.assertEQ(holder.strictField1, val8);
        Asserts.assertEQ(holder.strictField2, val8);
        Asserts.assertEQ(holder.strictField3, val8NullFree);
        Asserts.assertEQ(holder.strictField4, val8NullFree);
        Asserts.assertEQ(holder.strictField5, val8NullFree);
        Asserts.assertEQ(holder.strictField6, val8NullFree);

        Asserts.assertEQ(holder.strictField7, twoBytes);
        Asserts.assertEQ(holder.strictField8, twoBytes);
        Asserts.assertEQ(holder.strictField9, twoBytesNullFree);
        Asserts.assertEQ(holder.strictField10, twoBytesNullFree);
        Asserts.assertEQ(holder.strictField11, twoBytesNullFree);
        Asserts.assertEQ(holder.strictField12, twoBytesNullFree);
    }

    public static void main(String[] args) {
        TestFieldNullMarkers t = new TestFieldNullMarkers();
        t.testOSR();

        final int LIMIT = 50_000;
        for (int i = -50_000; i < LIMIT; ++i) {
            t.field1 = null;
            Asserts.assertEQ(t.testGet1(), null);

            boolean useNull = (i % 2) == 0;
            MyValue2 val2 = useNull ? null : new MyValue2((byte)i);
            MyValue1 val = new MyValue1((byte)i, val2, val2);
            t.field1 = val;
            Asserts.assertEQ(t.testGet1().x, val.x);
            Asserts.assertEQ(t.testGet1().val1, val2);
            Asserts.assertEQ(t.testGet1().val2, val2);

            Asserts.assertTrue(t.testACmp(val2));

            t.testSet1(null);
            Asserts.assertEQ(t.field1, null);

            t.testSet1(val);
            Asserts.assertEQ(t.field1.x, val.x);
            Asserts.assertEQ(t.field1.val1, val2);
            Asserts.assertEQ(t.field1.val2, val2);

            t.testDeopt1((byte)i, null, null, false);

            t.field2 = null;
            Asserts.assertEQ(t.testGet2(), null);

            MyValue3 val3 = useNull ? null : new MyValue3((byte)i);
            MyValue4 val4 = new MyValue4(val3, val3);
            t.field2 = val4;
            Asserts.assertEQ(t.testGet2().val1, val3);
            Asserts.assertEQ(t.testGet2().val2, val3);

            t.testSet2(null);
            Asserts.assertEQ(t.testGet2(), null);

            t.testSet2(val4);
            Asserts.assertEQ(t.testGet2().val1, val3);
            Asserts.assertEQ(t.testGet2().val2, val3);

            t.testDeopt2((byte)i, null, null, false);

            t.field3 = null;
            Asserts.assertEQ(t.testGet3(), null);

            boolean useNull_1 = (i % 4) == 0;
            boolean useNull_2 = (i % 4) == 1;
            boolean useNull_3 = (i % 4) == 2;
            MyValue5C val5_3 = useNull_3 ? null : new MyValue5C((byte)i);
            MyValue5B val5_2 = useNull_2 ? null : new MyValue5B((byte)i, val5_3);
            MyValue5A val5_1 = useNull_1 ? null : new MyValue5A((byte)i, val5_2);
            MyValue5 val5 = new MyValue5((byte)i, val5_1);
            t.field3 = val5;
            Asserts.assertEQ(t.testGet3().x, val5.x);
            if (useNull_1) {
                Asserts.assertEQ(t.testGet3().val, null);
            } else {
                Asserts.assertEQ(t.testGet3().val.x, val5_1.x);
                if (useNull_2) {
                    Asserts.assertEQ(t.testGet3().val.val, null);
                } else {
                    Asserts.assertEQ(t.testGet3().val.val.x, val5_2.x);
                    if (useNull_3) {
                        Asserts.assertEQ(t.testGet3().val.val.val, null);
                    } else {
                        Asserts.assertEQ(t.testGet3().val.val.val.x, val5_3.x);
                    }
                }
            }

            t.testSet3(null);
            Asserts.assertEQ(t.field3, null);

            t.testSet3(val5);
            Asserts.assertEQ(t.testGet3().x, val5.x);
            if (useNull_1) {
                Asserts.assertEQ(t.testGet3().val, null);
            } else {
                Asserts.assertEQ(t.testGet3().val.x, val5_1.x);
                if (useNull_2) {
                    Asserts.assertEQ(t.testGet3().val.val, null);
                } else {
                    Asserts.assertEQ(t.testGet3().val.val.x, val5_2.x);
                    if (useNull_3) {
                        Asserts.assertEQ(t.testGet3().val.val.val, null);
                    } else {
                        Asserts.assertEQ(t.testGet3().val.val.val.x, val5_3.x);
                    }
                }
            }
            t.testDeopt3((byte)i, null, null, null, null, false);

            t.field4 = null;
            Asserts.assertEQ(t.testGet4(), null);

            MyValueEmpty empty = useNull ? null : new MyValueEmpty();
            MyValue6 val6 = new MyValue6(empty);
            t.field4 = val6;
            Asserts.assertEQ(t.testGet4().val, empty);

            t.testSet4(null);
            Asserts.assertEQ(t.testGet4(), null);

            t.testSet4(val6);
            Asserts.assertEQ(t.testGet4().val, empty);

            t.testDeopt4(null, null, null, false);

            t.field5 = null;
            Asserts.assertEQ(t.testGet5(), null);

            empty = ((i % 3) == 0) ? null : new MyValueEmpty();
            val6 = ((i % 3) == 1) ? null : new MyValue6(empty);
            MyValue7 val7 = new MyValue7(val6);
            t.field5 = val7;
            Asserts.assertEQ(t.testGet5().val, val6);

            t.testSet5(null);
            Asserts.assertEQ(t.testGet5(), null);

            t.testSet5(val7);
            Asserts.assertEQ(t.testGet5().val, val6);

            t.testDeopt5(null, null, null, null, false);

            // Check accesses with constant value
            t.field1 = VAL1;
            Asserts.assertEQ(t.field1.x, VAL1.x);
            Asserts.assertEQ(t.field1.val1, VAL1.val1);
            Asserts.assertEQ(t.field1.val2, VAL1.val2);

            t.field2 = VAL4;
            Asserts.assertEQ(t.field2.val1, VAL4.val1);
            Asserts.assertEQ(t.field2.val2, VAL4.val2);

            t.field3 = VAL5;
            Asserts.assertEQ(t.field3.x, VAL5.x);
            Asserts.assertEQ(t.field3.val.x, VAL5.val.x);
            Asserts.assertEQ(t.field3.val.val.x, VAL5.val.val.x);
            Asserts.assertEQ(t.field3.val.val.val.x, VAL5.val.val.val.x);

            t.field4 = VAL6;
            Asserts.assertEQ(t.field4.val, VAL6.val);

            t.field5 = VAL7;
            Asserts.assertEQ(t.field5.val, VAL7.val);

            // Some more values classes with different flavors of primitive fields
            t.field6 = null;
            Asserts.assertEQ(t.field6, null);
            t.field6 = new MyValue8((byte)i);
            Asserts.assertEQ(t.field6.b, (byte)i);
            t.field7 = null;
            Asserts.assertEQ(t.field7, null);
            t.field7 = new MyValue9((short)i);
            Asserts.assertEQ(t.field7.s, (short)i);
            t.field8 = null;
            Asserts.assertEQ(t.field8, null);
            t.field8 = new MyValue10(i);
            Asserts.assertEQ(t.field8.i, i);
            t.field9 = null;
            Asserts.assertEQ(t.field9, null);
            t.field9 = new MyValue11((float)i);
            Asserts.assertEQ(t.field9.f, (float)i);
            t.field10 = null;
            Asserts.assertEQ(t.field10, null);
            t.field10 = new MyValue12((char)i);
            Asserts.assertEQ(t.field10.c, (char)i);
            t.field11 = null;
            Asserts.assertEQ(t.field11, null);
            t.field11 = new MyValue13((i % 2) == 0);
            Asserts.assertEQ(t.field11.b, (i % 2) == 0);

            // Write the fields again and check that we don't overwrite other fields
            t.checkFields(i);
            t.field6 = new MyValue8((byte)i);
            t.checkFields(i);
            t.field7 = new MyValue9((short)i);
            t.checkFields(i);
            t.field8 = new MyValue10(i);
            t.checkFields(i);
            t.field9 = new MyValue11((float)i);
            t.checkFields(i);
            t.field10 = new MyValue12((char)i);
            t.checkFields(i);
            t.field11 = new MyValue13((i % 2) == 0);
            t.checkFields(i);

            testNPE1();
            testNPE2();

            t.testBounds(i);

            // Null-free, flat, atomic
            MyValue8 val8 = new MyValue8((byte)i);
            t.field12 = val8;
            Asserts.assertEQ(t.field12.b, (byte)i);

            try {
                t.field12 = null;
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException npe) {
                // Expected
            }

            // Null-free, flat with both nullable and null-free fields
            t.field13 = new MyValue14(val8, val8);
            Asserts.assertEQ(t.field13.nullfree, val8);
            Asserts.assertEQ(t.field13.nullable, val8);

            t.field13 = new MyValue14(val8, null);
            Asserts.assertEQ(t.field13.nullfree, val8);
            Asserts.assertEQ(t.field13.nullable, null);

            try {
                t.field13 = new MyValue14(null, null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException npe) {
                // Expected
            }
            try {
                t.field13 = null;
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException npe) {
                // Expected
            }

            // Nullable, atomic, flat with both nullable and null-free fields
            t.field14 = null;
            Asserts.assertEQ(t.field14, null);

            t.field14 = new MyValue14(val8, val8);
            Asserts.assertEQ(t.field14.nullfree, val8);
            Asserts.assertEQ(t.field14.nullable, val8);

            t.field14 = new MyValue14(val8, null);
            Asserts.assertEQ(t.field14.nullfree, val8);
            Asserts.assertEQ(t.field14.nullable, null);

            try {
                t.field14 = new MyValue14(null, null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException npe) {
                // Expected
            }

            // Nullable, (atomic), flat with both nullable and null-free fields
            t.field15 = null;
            Asserts.assertEQ(t.field15, null);

            t.field15 = new MyValue14(val8, val8);
            Asserts.assertEQ(t.field15.nullfree, val8);
            Asserts.assertEQ(t.field15.nullable, val8);

            t.field15 = new MyValue14(val8, null);
            Asserts.assertEQ(t.field15.nullfree, val8);
            Asserts.assertEQ(t.field15.nullable, null);

            try {
                t.field15 = new MyValue14(null, null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException npe) {
                // Expected
            }

            // Null-free, atomic, flat with both nullable and null-free fields
            t.field16 = new MyValue14(val8, val8);
            Asserts.assertEQ(t.field16.nullfree, val8);
            Asserts.assertEQ(t.field16.nullable, val8);

            t.field16 = new MyValue14(val8, null);
            Asserts.assertEQ(t.field16.nullfree, val8);
            Asserts.assertEQ(t.field16.nullable, null);

            try {
                t.field16 = new MyValue14(null, null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException npe) {
                // Expected
            }
            try {
                t.field16 = null;
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException npe) {
                // Expected
            }

            MyValue15 val15 = new MyValue15(new MyClass(i));
            t.testWriteOopFields1(val15);
            if (i > (LIMIT - 50)) {
                // After warmup, produce some garbage to trigger GC
                produceGarbage();
            }
            Asserts.assertEQ(t.field17.obj.x, i);
            Asserts.assertEQ(t.field18.obj.x, i);

            MyValue16 val16 = new MyValue16(new MyClass(i), new MyClass(i));
            t.testWriteOopFields2(val16);
            if (i > (LIMIT - 50)) {
                // After warmup, produce some garbage to trigger GC
                produceGarbage();
            }
            Asserts.assertEQ(t.field19.obj1.x, i);
            Asserts.assertEQ(t.field19.obj2.x, i);

            MyValue17 val17 = new MyValue17(new MyClass(i), (byte)i, (byte)i);
            t.testWriteOopFields3(val17);
            if (i > (LIMIT - 50)) {
                // After warmup, produce some garbage to trigger GC
                produceGarbage();
            }
            Asserts.assertEQ(t.field20.obj.x, i);
            Asserts.assertEQ(t.field20.b1, (byte)i);
            Asserts.assertEQ(t.field20.b2, (byte)i);
            Asserts.assertEQ(t.field21.obj.x, i);
            Asserts.assertEQ(t.field21.b1, (byte)i);
            Asserts.assertEQ(t.field21.b2, (byte)i);

            Asserts.assertEQ(t.emptyField1, new MyValueEmpty());
            Asserts.assertEQ(t.emptyField2, new MyValueEmpty());

            // Test empty fields
            t.emptyField3 = new MyValueEmpty();
            t.emptyField4 = new MyValueEmpty();
            Asserts.assertEQ(t.emptyField3, new MyValueEmpty());
            Asserts.assertEQ(t.emptyField4, new MyValueEmpty());
            t.emptyField3 = null;
            t.emptyField4 = null;
            Asserts.assertEQ(t.emptyField3, null);
            Asserts.assertEQ(t.emptyField4, null);

            t.testLoadingFromConstantHolder(i);

            // Verify that no out of bounds accesses happen
            t.testOutOfBoundsAccess(i);

            // Test strict fields
            TwoBytes twoBytes = new TwoBytes((byte)i, (byte)(i + 1));
            t.testStrictFields(new StrictFieldHolder(val8, val8, twoBytes, twoBytes), val8, val8, twoBytes, twoBytes);
            t.testStrictFields(new StrictFieldHolder(null, val8, null, twoBytes), null, val8, null, twoBytes);
        }

        // Trigger deoptimization to check that re-materialization takes the null marker into account
        byte x = (byte)42;
        t.testDeopt1(x, new MyValue1(x, new MyValue2(x), new MyValue2(x)), new MyValue1(x, null, null), true);
        t.testDeopt2(x, new MyValue4(new MyValue3(x), new MyValue3(x)), new MyValue4(null, null), true);

        MyValue5 val1 = new MyValue5(x, new MyValue5A(x, new MyValue5B(x, new MyValue5C(x))));
        MyValue5 val2 = new MyValue5(x, new MyValue5A(x, new MyValue5B(x, null)));
        MyValue5 val3 = new MyValue5(x, new MyValue5A(x, null));
        MyValue5 val4 = new MyValue5(x, null);
        t.testDeopt3(x, val1, val2, val3, val4, true);

        MyValue6 val5 = new MyValue6(new MyValueEmpty());
        MyValue6 val6 = new MyValue6(null);
        MyValue6 val7 = null;
        t.testDeopt4(val5, val6, val7, true);

        MyValue7 val8 = new MyValue7(new MyValue6(new MyValueEmpty()));
        MyValue7 val9 = new MyValue7(new MyValue6(null));
        MyValue7 val10 = new MyValue7(null);
        MyValue7 val11 = null;
        t.testDeopt5(val8, val9, val10, val11, false);
    }
}

