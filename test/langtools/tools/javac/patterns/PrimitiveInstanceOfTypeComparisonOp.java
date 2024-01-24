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
 * @bug 8304487
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Preview)
 * @enablePreview
 * @compile PrimitiveInstanceOfTypeComparisonOp.java
 * @run main/othervm PrimitiveInstanceOfTypeComparisonOp
 */
public class PrimitiveInstanceOfTypeComparisonOp {

    public static void main(String[] args) {
        assertEquals(true,  identityPrimitiveConversion());
        assertEquals(true,  wideningPrimitiveConversion());
        assertEquals(true,  narrowingPrimitiveConversion());
        assertEquals(true,  wideningAndNarrowingPrimitiveConversion());
        assertEquals(true,  boxingConversion());
        assertEquals(true,  boxingAndWideningReferenceConversion());
        assertEquals(true,  unboxing());
        assertEquals(true,  unboxingWithObject());
        assertEquals(true,  wideningReferenceConversionUnboxing(42));
        assertEquals(true,  wideningReferenceConversionUnboxingAndWideningPrimitive(42));
        assertEquals(true,  unboxingAndWideningPrimitiveExact());
        assertEquals(false, unboxingAndWideningPrimitiveNotExact());
        assertEquals(true,  unboxingWhenNullAndWideningPrimitive());
        assertEquals(true,  narrowingAndUnboxing());
        assertEquals(true,  patternExtractRecordComponent());
        assertEquals(true,  exprMethod());
        assertEquals(true,  exprStaticallyQualified());
    }

    public static boolean identityPrimitiveConversion() {
        int i = 42;
        return i instanceof int;
    }

    public static boolean wideningPrimitiveConversion() {
        byte b = (byte) 42;
        short s = (short) 42;
        char c = 'a';

        return b instanceof int && s instanceof int && c instanceof int;
    }

    public static boolean narrowingPrimitiveConversion() {
        long l_within_int_range = 42L;
        long l_outside_int_range = 999999999999999999L;

        return l_within_int_range instanceof int && !(l_outside_int_range instanceof int);
    }

    public static boolean wideningAndNarrowingPrimitiveConversion() {
        byte b = (byte) 42;
        byte b2 = (byte) -42;
        char c = (char) 42;
        return b instanceof char && c instanceof byte && !(b2 instanceof char);
    }

    public static boolean boxingConversion() {
        int i = 42;

        return i instanceof Integer;
    }

    public static boolean boxingAndWideningReferenceConversion() {
        int i = 42;
        return i instanceof Object &&
                i instanceof Number &&
                i instanceof Comparable;
    }

    public static boolean unboxing() {
        Integer i = Integer.valueOf(1);
        return i instanceof int;
    }

    public static boolean unboxingWithObject() {
        Object o1 = (int) 42;
        Object o2 = (byte) 42;

        return o1 instanceof int i1 &&
                o2 instanceof byte b1 &&
                !(o1 instanceof byte b2 &&
                !(o2 instanceof int i2));
    }

    public static <T extends Integer> boolean wideningReferenceConversionUnboxing(T i) {
        return i instanceof int;
    }

    public static <T extends Integer> boolean wideningReferenceConversionUnboxingAndWideningPrimitive(T i) {
        return i instanceof double;
    }

    public static boolean unboxingAndWideningPrimitiveExact() {
        Byte b = Byte.valueOf((byte)42);
        Short s = Short.valueOf((short)42);
        Character c = Character.valueOf('a');

        return (b instanceof int) && (s instanceof int) && (c instanceof int);
    }

    public static boolean unboxingAndWideningPrimitiveNotExact() {
        int smallestIntNotRepresentable = 16777217; // 2^24 + 1
        Integer i = Integer.valueOf(smallestIntNotRepresentable);

        return i instanceof float;
    }

    public static boolean unboxingWhenNullAndWideningPrimitive() {
        Byte b = null;
        Short s = null;
        Character c = null;

        return !(b instanceof int) && !(s instanceof int) && !(c instanceof int);
    }

    public static boolean narrowingAndUnboxing() {
        Number n = Byte.valueOf((byte) 42);

        return n instanceof byte;
    }

    public record P(int i) { }
    public static boolean patternExtractRecordComponent() {
        Object p = new P(42);
        if (p instanceof P(byte b)) {
            return b == 42;
        }
        return false;
    }

    public static int meth() {return 42;}
    public static boolean exprMethod() {
        return meth() instanceof int;
    }

    public class A1 {
        public static int i = 42;
    }
    public static boolean exprStaticallyQualified() {
        return A1.i instanceof int;
    }

    static void assertEquals(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }
}
