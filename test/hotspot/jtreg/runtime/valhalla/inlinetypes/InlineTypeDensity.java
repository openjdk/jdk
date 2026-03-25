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

import java.lang.management.MemoryPoolMXBean;

import com.sun.jdi.NativeMethodException;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;


/**
 * @test id=compressed-oops
 * @summary Heap density test for InlineTypes
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @enablePreview
 * @compile InlineTypeDensity.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UseArrayFlattening -XX:+UseCompressedOops
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI runtime.valhalla.inlinetypes.InlineTypeDensity
 */

/**
 * @test id=no-compressed-oops
 * @summary Heap density test for InlineTypes
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @enablePreview
 * @compile InlineTypeDensity.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UseArrayFlattening -XX:-UseCompressedOops
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI runtime.valhalla.inlinetypes.InlineTypeDensity
 */

/**
 * @test id=no-explicit-compression
 * @summary Heap density test for InlineTypes
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @enablePreview
 * @compile InlineTypeDensity.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UseArrayFlattening
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI runtime.valhalla.inlinetypes.InlineTypeDensity
 */

/**
 * @test id=force-non-tearable
 * @summary Heap density test for InlineTypes
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @enablePreview
 * @compile InlineTypeDensity.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UseArrayFlattening
 *                   -Xbootclasspath/a:. -XX:ForceNonTearable=*
 *                   -XX:+WhiteBoxAPI runtime.valhalla.inlinetypes.InlineTypeDensity
 */

public class InlineTypeDensity {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final boolean VM_FLAG_FORCENONTEARABLE = WHITE_BOX.getStringVMFlag("ForceNonTearable").equals("*");

    public InlineTypeDensity() {
        if (WHITE_BOX.getBooleanVMFlag("UseArrayFlattening") != true) {
            throw new IllegalStateException("UseArrayFlattening should be true");
        }
    }

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

    public void ensureArraySizeWin() {
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

    @LooselyConsistentValue
    static value class MyByte  { byte  v = 0; }
    @LooselyConsistentValue
    static value class MyShort { short v = 0; }
    @LooselyConsistentValue
    static value class MyInt   { int   v = 0; }
    @LooselyConsistentValue
    static value class MyLong  { long  v = 0; }

    void assertArraySameSize(Object a, Object b, int nofElements) {
        long aSize = WHITE_BOX.getObjectSize(a);
        long bSize = WHITE_BOX.getObjectSize(b);
        Asserts.assertEquals(aSize, bSize,
            a + "(" + aSize + " bytes) not equivalent size " +
            b + "(" + bSize + " bytes)" +
            (nofElements >= 0 ? " (array of " + nofElements + " elements)" : ""));
    }

    void testByteArraySizesSame(int[] testSizes) {
        for (int testSize : testSizes) {
            byte[] ba = new byte[testSize];
            MyByte[] mba = (MyByte[])ValueClass.newNullRestrictedNonAtomicArray(MyByte.class, testSize, new MyByte());
            assertArraySameSize(ba, mba, testSize);
        }
    }

    void testShortArraySizesSame(int[] testSizes) {
        for (int testSize : testSizes) {
            short[] sa = new short[testSize];
            MyShort[] msa = (MyShort[])ValueClass.newNullRestrictedNonAtomicArray(MyShort.class, testSize, new MyShort());
            assertArraySameSize(sa, msa, testSize);
        }
    }

    void testIntArraySizesSame(int[] testSizes) {
        for (int testSize : testSizes) {
            int[] ia = new int[testSize];
            MyInt[] mia = (MyInt[])ValueClass.newNullRestrictedNonAtomicArray(MyInt.class, testSize, new MyInt());
            assertArraySameSize(ia, mia, testSize);
        }
    }

    void testLongArraySizesSame(int[] testSizes) {
        for (int testSize : testSizes) {
            long[] la = new long[testSize];
            MyLong[] mla = (MyLong[])ValueClass.newNullRestrictedNonAtomicArray(MyLong.class, testSize, new MyLong());
            assertArraySameSize(la, mla, testSize);
        }
    }

    public void testPrimitiveArraySizesSame() {
        int[] testSizes = new int[] { 0, 1, 2, 3, 4, 7, 10, 257 };
        testByteArraySizesSame(testSizes);
        testShortArraySizesSame(testSizes);
        testIntArraySizesSame(testSizes);
        testLongArraySizesSame(testSizes);
    }

    @LooselyConsistentValue
    static value class bbValue { byte b = 0; byte b2 = 0;}
    @LooselyConsistentValue
    static value class bsValue { byte b = 0; short s = 0;}
    @LooselyConsistentValue
    static value class siValue { short s = 0; int i = 0;}
    @LooselyConsistentValue
    static value class ssiValue { short s = 0; short s2 = 0; int i = 0;}
    @LooselyConsistentValue
    static value class blValue { byte b = 0; long l = 0; }

    // Expect aligned array addressing to nearest pow2
    void testAlignedSize() {
        int testSize = 10;
        assertArraySameSize(new short[testSize], ValueClass.newNullRestrictedNonAtomicArray(bbValue.class, testSize, new bbValue()), testSize);
        assertArraySameSize(new long[testSize], ValueClass.newNullRestrictedNonAtomicArray(siValue.class, testSize, new siValue()), testSize);
        assertArraySameSize(new long[testSize], ValueClass.newNullRestrictedNonAtomicArray(ssiValue.class, testSize, new ssiValue()), testSize);
        assertArraySameSize(new long[testSize*2], ValueClass.newNullRestrictedNonAtomicArray(blValue.class, testSize, new blValue()), testSize);
        assertArraySameSize(new int[testSize], ValueClass.newNullRestrictedNonAtomicArray(bsValue.class, testSize, new bsValue()), testSize);
    }

    public void test() {
        ensureArraySizeWin();
        testPrimitiveArraySizesSame();
        if (!VM_FLAG_FORCENONTEARABLE) {
          testAlignedSize();
        }
    }

    public static void main(String[] args) {
        new InlineTypeDensity().test();
    }

}
