/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package runtime.valhalla.inlinetypes;

import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;


/**
 * @test id=compressed-oops
 * @summary Heap density test for InlineTypes
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.misc
 * @enablePreview
 * @compile InlineTypeDensity.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UseArrayFlattening -XX:+UseCompressedOops
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI runtime.valhalla.inlinetypes.InlineTypeDensity
 */

/**
 * @test id=no-compressed-oops
 * @summary Heap density test for InlineTypes
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.misc
 * @enablePreview
 * @compile InlineTypeDensity.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UseArrayFlattening -XX:-UseCompressedOops
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI runtime.valhalla.inlinetypes.InlineTypeDensity
 */

/**
 * @test id=no-explicit-compression
 * @summary Heap density test for InlineTypes
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.misc
 * @enablePreview
 * @compile InlineTypeDensity.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UseArrayFlattening
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI runtime.valhalla.inlinetypes.InlineTypeDensity
 */

/**
 * @test id=force-non-tearable
 * @summary Heap density test for InlineTypes
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.misc
 * @enablePreview
 * @compile InlineTypeDensity.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+UseArrayFlattening
 *                   -Xbootclasspath/a:. -XX:ForceNonTearable=*
 *                   -XX:+WhiteBoxAPI runtime.valhalla.inlinetypes.InlineTypeDensity
 */

public class InlineTypeDensity {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final boolean VM_FLAG_FORCENONTEARABLE = WHITE_BOX.getStringVMFlag("ForceNonTearable").equals("*");
    private static final boolean ARRAY_FLATTENING_ON = WHITE_BOX.getBooleanVMFlag("UseArrayFlattening");

    interface LocalDate {
        public int getYear();
        public short getMonth();
        public short getDay();
    }

    interface LocalTime {
        public byte getHour();
        public byte getMinute();
        public byte getSecond();
        public int getNano();
    }

    interface LocalDateTime extends LocalDate, LocalTime {}

    @LooselyConsistentValue
    static value class LocalDateValue implements LocalDate {
        final int   year;
        final short month;
        final short day;

        public LocalDateValue(int year, short month, short day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }

        public int   getYear()  { return year; }
        public short getMonth() { return month; }
        public short getDay()   { return day; }

    }

    @LooselyConsistentValue
    static value class LocalTimeValue implements LocalTime {
        final byte hour;
        final byte minute;
        final byte second;
        final int nano;

        public LocalTimeValue(byte hour, byte minute, byte second, int nano) {
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.nano = nano;
        }

        public byte getHour()   { return hour; }
        public byte getMinute() { return minute; }
        public byte getSecond() { return second; }
        public int getNano()    { return nano; }

    }

    @LooselyConsistentValue
    static value class LocalDateTimeValue implements LocalDateTime {
        @NullRestricted
        LocalDateValue date;
        @NullRestricted
        LocalTimeValue time;

        public LocalDateTimeValue(LocalDateValue date, LocalTimeValue time) {
            this.date = date;
            this.time = time;
        }

        public int   getYear()  { return date.year; }
        public short getMonth() { return date.month; }
        public short getDay()   { return date.day; }

        public byte getHour()   { return time.hour; }
        public byte getMinute() { return time.minute; }
        public byte getSecond() { return time.second; }
        public int getNano()    { return time.nano; }

    }

    static final class LocalDateClass implements LocalDate {
        final int   year;
        final short month;
        final short day;

        LocalDateClass(int year, short month, short day) {
            this.year  = year;
            this.month = month;
            this.day   = day;
        }

        public int   getYear()  { return year; }
        public short getMonth() { return month; }
        public short getDay()   { return day; }
    }

    static final class LocalTimeClass implements LocalTime {
        final byte hour;
        final byte minute;
        final byte second;
        final int nano;

        LocalTimeClass(byte hour, byte minute, byte second, int nano) {
            this.hour   = hour;
            this.minute = minute;
            this.second = second;
            this.nano   = nano;
        }

        public byte getHour()   { return hour; }
        public byte getMinute() { return minute; }
        public byte getSecond() { return second; }
        public int getNano()    { return nano; }
    }

    static final class LocalDateTimeClass implements LocalDateTime {
        final LocalDateClass date;
        final LocalTimeClass time;

        LocalDateTimeClass(LocalDateClass date, LocalTimeClass time) {
            this.date = date;
            this.time = time;
        }

        public LocalDateClass getDate() { return date; }
        public LocalTimeClass getTime() { return time; }

        public int   getYear()  { return date.year; }
        public short getMonth() { return date.month; }
        public short getDay()   { return date.day; }

        public byte getHour()   { return time.hour; }
        public byte getMinute() { return time.minute; }
        public byte getSecond() { return time.second; }
        public int getNano()    { return time.nano; }
    }

    private void ensureArraySizeWin() {
        int arrayLength = 1000;
        System.out.println("ensureArraySizeWin for length " + arrayLength);
        LocalDateTimeClass[] objectArray = new LocalDateTimeClass[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            objectArray[i] = new LocalDateTimeClass(new LocalDateClass(0, (short)0, (short)0),
                    new LocalTimeClass((byte)0, (byte)0, (byte)0, 0));
        }

        long objectArraySize = WHITE_BOX.getObjectSize(objectArray);
        System.out.println("Empty object array size: " + objectArraySize);
        objectArraySize += (arrayLength *
                (WHITE_BOX.getObjectSize(objectArray[0]) +
                        WHITE_BOX.getObjectSize(objectArray[0].getDate()) +
                        WHITE_BOX.getObjectSize(objectArray[0].getTime())));

        LocalDateTimeValue[] flatArray = new LocalDateTimeValue[arrayLength];
        // CMH: add "isFlatValueArray" to WhiteBox API, to ensure we are correctly account size

        long flatArraySize = WHITE_BOX.getObjectSize(flatArray);
        System.out.println("Object array and elements: " + objectArraySize + " versus Flat Array: " + flatArraySize);
        Asserts.assertLessThan(flatArraySize, objectArraySize, "Flat array accounts for more heap than object array + elements !");
    }

    private static void assertFlat(String name, Object[] value, boolean shouldBeFlat) {
        String expectation = shouldBeFlat ? "be flat" : "not be flat";
        Asserts.assertEquals(ValueClass.isFlatArray(value), shouldBeFlat,
                "%s array should %s".formatted(name, expectation));
    }

    @LooselyConsistentValue
    static value class MyByte  { byte  v = 0; }
    @LooselyConsistentValue
    static value class MyShort { short v = 0; }
    @LooselyConsistentValue
    static value class MyInt   { int   v = 0; }
    @LooselyConsistentValue
    static value class MyLong  { long  v = 0; }

    private void testPrimitiveLikeArraysAreFlattened() {
        int[] testSizes = new int[] { 0, 1, 2, 3, 4, 7, 10, 257 };
        for (int testSize : testSizes) {
            MyByte[] mba = (MyByte[])ValueClass.newNullRestrictedNonAtomicArray(MyByte.class, testSize, new MyByte());
            MyShort[] msa = (MyShort[])ValueClass.newNullRestrictedNonAtomicArray(MyShort.class, testSize, new MyShort());
            MyInt[] mia = (MyInt[])ValueClass.newNullRestrictedNonAtomicArray(MyInt.class, testSize, new MyInt());
            MyLong[] mla = (MyLong[])ValueClass.newNullRestrictedNonAtomicArray(MyLong.class, testSize, new MyLong());

            assertFlat("MyByte", mba, ARRAY_FLATTENING_ON);
            assertFlat("MyShort", msa, ARRAY_FLATTENING_ON);
            assertFlat("MyInt", mia, ARRAY_FLATTENING_ON);
            assertFlat("MyLong", mla, ARRAY_FLATTENING_ON);

            Asserts.assertTrue(UNSAFE.arrayInstanceBaseOffset(mba) % 8 == 0);
            Asserts.assertTrue(UNSAFE.arrayInstanceBaseOffset(msa) % 8 == 0);
            Asserts.assertTrue(UNSAFE.arrayInstanceBaseOffset(mia) % 8 == 0);
            Asserts.assertTrue(UNSAFE.arrayInstanceBaseOffset(mla) % 8 == 0);
        }
    }

    @LooselyConsistentValue
    static value class MyBB { byte b = 0; byte b2 = 0;}
    @LooselyConsistentValue
    static value class MyBS { byte b = 0; short s = 0;}
    @LooselyConsistentValue
    static value class MySI { short s = 0; int i = 0;}
    @LooselyConsistentValue
    static value class MySSI { short s = 0; short s2 = 0; int i = 0;}

    private void testTupleArraysAreFlattened() {
        int[] testSizes = new int[] { 0, 1, 2, 3, 4, 7, 10, 257 };
        for (int testSize : testSizes) {
            MyBB[] mbb = (MyBB[])ValueClass.newNullRestrictedNonAtomicArray(MyBB.class, testSize, new MyBB());
            MyBS[] mbs = (MyBS[])ValueClass.newNullRestrictedNonAtomicArray(MyBS.class, testSize, new MyBS());
            MySI[] msi = (MySI[])ValueClass.newNullRestrictedNonAtomicArray(MySI.class, testSize, new MySI());
            MySSI[] mssi = (MySSI[])ValueClass.newNullRestrictedNonAtomicArray(MySSI.class, testSize, new MySSI());

            assertFlat("MyBB", mbb, ARRAY_FLATTENING_ON);
            assertFlat("MyBS", mbs, ARRAY_FLATTENING_ON);
            assertFlat("MySI", msi, ARRAY_FLATTENING_ON);
            assertFlat("MySSI", mssi, ARRAY_FLATTENING_ON);

            Asserts.assertTrue(UNSAFE.arrayInstanceBaseOffset(mbb) % 8 == 0);
            Asserts.assertTrue(UNSAFE.arrayInstanceBaseOffset(mbs) % 8 == 0);
            Asserts.assertTrue(UNSAFE.arrayInstanceBaseOffset(msi) % 8 == 0);
            Asserts.assertTrue(UNSAFE.arrayInstanceBaseOffset(mssi) % 8 == 0);
        }
    }

    private void test() {
        ensureArraySizeWin();
        testPrimitiveLikeArraysAreFlattened();
        testTupleArraysAreFlattened();
    }

    public static void main(String[] args) {
        new InlineTypeDensity().test();
    }

}
