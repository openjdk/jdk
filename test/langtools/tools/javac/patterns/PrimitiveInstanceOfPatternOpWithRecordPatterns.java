/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8304487 8327683 8330387
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Preview)
 * @enablePreview
 * @compile PrimitiveInstanceOfPatternOpWithRecordPatterns.java
 * @run main/othervm PrimitiveInstanceOfPatternOpWithRecordPatterns
 */
public class PrimitiveInstanceOfPatternOpWithRecordPatterns {

    public static void main(String[] args) {
        assertEquals(true,  identityPrimitiveConversion());
        assertEquals(true,  wideningPrimitiveConversion());
        assertEquals(true,  narrowingPrimitiveConversion());
        assertEquals(true,  wideningAndNarrowingPrimitiveConversion());
        assertEquals(true,  boxingConversion());
        assertEquals(true,  boxingAndWideningReferenceConversion());
        assertEquals(true,  unboxing());
        assertEquals(true,  unboxingWithObject());
        assertEquals(true,  wideningReferenceConversionUnboxing());
        assertEquals(true,  wideningReferenceConversionUnboxing2());
        assertEquals(true,  wideningReferenceConversionUnboxing3());
        assertEquals(true,  wideningReferenceConversionUnboxingAndWideningPrimitive());
        assertEquals(true,  unboxingAndWideningPrimitiveExact());
        assertEquals(false, unboxingAndWideningPrimitiveNotExact());
        assertEquals(true,  unboxingWhenNullAndWideningPrimitive());
        assertEquals(true,  narrowingAndUnboxing());
    }

    public static boolean identityPrimitiveConversion() {
        R_int r = new R_int(42);
        return r instanceof R_int(int _);
    }

    public static boolean wideningPrimitiveConversion() {
        R_byte b = new R_byte((byte) 42);
        R_short s = new R_short((short) 42);
        R_char c = new R_char('a');

        return b instanceof R_byte(int _) && s instanceof R_short(int _) && c instanceof R_char(int _);
    }

    public static boolean narrowingPrimitiveConversion() {
        R_long l_within_int_range = new R_long(42L);
        R_long l_outside_int_range = new R_long(999999999999999999L);

        return l_within_int_range instanceof R_long(int _) && !(l_outside_int_range instanceof R_long(int _));
    }

    public static boolean wideningAndNarrowingPrimitiveConversion() {
        R_byte b = new R_byte((byte) 42);
        R_byte b2 = new R_byte((byte) -42);
        R_char c = new R_char((char) 42);
        return b instanceof R_byte(char _) && c instanceof R_char(byte _) && !(b2 instanceof R_byte(char _));
    }

    public static boolean boxingConversion() {
        R_int i = new R_int(42);

        return i instanceof R_int(Integer _);
    }

    public static boolean boxingAndWideningReferenceConversion() {
        R_int i = new R_int(42);
        return i instanceof R_int(Object _) &&
                i instanceof R_int(Number _) &&
                i instanceof R_int(Comparable _);
    }

    public static boolean unboxing() {
        R_Integer i = new R_Integer(Integer.valueOf(1));
        return i instanceof R_Integer(int _);
    }

    public static boolean unboxingWithObject() {
        R_Object o1 = new R_Object((int) 42);
        R_Object o2 = new R_Object((byte) 42);

        return o1 instanceof R_Object(int i1) &&
                o2 instanceof R_Object(byte b1) &&
                !(o1 instanceof R_Object(byte b2) &&
                !(o2 instanceof R_Object(int i2)));
    }

    public static boolean wideningReferenceConversionUnboxing() {
        R_generic<Integer> i = new R_generic<Integer>(42);
        return i instanceof R_generic(int _);
    }

    public static boolean wideningReferenceConversionUnboxing2() {
        R_generic2<Byte> i = new R_generic2<Byte>(Byte.valueOf((byte) 42));
        return i instanceof R_generic2(byte _);
    }

    public static boolean wideningReferenceConversionUnboxing3() {
        R_generic<Integer> i = new R_generic<Integer>(0x1000000);
        return i instanceof R_generic(float _);
    }

    public static boolean wideningReferenceConversionUnboxingAndWideningPrimitive() {
        R_generic<Integer> i = new R_generic<Integer>(42);
        return i instanceof R_generic(double _);
    }

    public static boolean unboxingAndWideningPrimitiveExact() {
        R_ByteValue b = new R_ByteValue(Byte.valueOf((byte)42));
        R_ShortValue s = new R_ShortValue(Short.valueOf((short)42));
        R_CharacterValue c = new R_CharacterValue(Character.valueOf('a'));

        return (b instanceof R_ByteValue(int _)) && (s instanceof R_ShortValue(int _)) && (c instanceof R_CharacterValue(int _));
    }

    public static boolean unboxingAndWideningPrimitiveNotExact() {
        int smallestIntNotRepresentable = 16777217; // 2^24 + 1
        R_Integer i = new R_Integer(Integer.valueOf(smallestIntNotRepresentable));

        return i instanceof R_Integer(float _);
    }

    public static boolean unboxingWhenNullAndWideningPrimitive() {
        R_ByteValue b = new R_ByteValue(null);
        R_ShortValue s = new R_ShortValue(null);
        R_CharacterValue c = new R_CharacterValue(null);

        return !(b instanceof R_ByteValue(int _)) && !(s instanceof R_ShortValue(int _)) && !(c instanceof R_CharacterValue(int _));
    }

    public static boolean narrowingAndUnboxing() {
        R_Number n = new R_Number(Byte.valueOf((byte) 42));

        return n instanceof R_Number(byte _);
    }

    static void assertEquals(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }

    record R_int(int i) {}
    record R_byte(byte b) {}
    record R_short(short b) {}
    record R_char(char c) {}
    record R_long(long l) {}
    record R_Integer(Integer i) {}
    record R_Object(Object i) {}
    record R_generic<T extends Integer>(T i) {}
    record R_generic2<T extends Byte>(T i) {}

    record R_ByteValue(Byte b) {}
    record R_ShortValue(Short s) {}
    record R_CharacterValue(Character s) {}
    record R_Number(Number s) {}
}
