/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304487 8325257 8327683 8330387
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Preview)
 * @enablePreview
 * @compile PrimitiveInstanceOfPatternOpWithTopLevelPatterns.java
 * @run main/othervm PrimitiveInstanceOfPatternOpWithTopLevelPatterns
 */
public class PrimitiveInstanceOfPatternOpWithTopLevelPatterns {
    public static final int qualI = 42;

    public static void main(String[] args) {
        assertEquals(true,  qualifiedExprConversion());
        assertEquals(true,  identityPrimitiveConversion());
        assertEquals(true,  wideningPrimitiveConversion());
        assertEquals(true,  narrowingPrimitiveConversion());
        assertEquals(true,  wideningAndNarrowingPrimitiveConversion());
        assertEquals(true,  boxingConversion());
        assertEquals(true,  boxingAndWideningReferenceConversion());
        assertEquals(true,  unboxing());
        assertEquals(true,  unboxingWithObject());
        assertEquals(true,  wideningReferenceConversionUnboxing(42));
        assertEquals(true,  wideningReferenceConversionUnboxing2(Byte.valueOf((byte) 42)));
        assertEquals(true,  wideningReferenceConversionUnboxing3(0x1000000));
        assertEquals(true,  wideningReferenceConversionUnboxingAndWideningPrimitive(42));
        assertEquals(true,  unboxingAndWideningPrimitiveExact());
        assertEquals(false, unboxingAndWideningPrimitiveNotExact());
        assertEquals(true,  unboxingWhenNullAndWideningPrimitive());
        assertEquals(true,  narrowingAndUnboxing());
        assertEquals(true,  patternExtractRecordComponent());
        assertEquals(true,  exprMethod());
        assertEquals(true,  exprStaticallyQualified());
    }

    public static boolean qualifiedExprConversion() {
        return PrimitiveInstanceOfTypeComparisonOp.qualI instanceof int;
    }

    public static boolean identityPrimitiveConversion() {
        int i = 42;
        return i instanceof int ii;
    }

    public static boolean wideningPrimitiveConversion() {
        byte b = (byte) 42;
        short s = (short) 42;
        char c = 'a';

        return b instanceof int bb && s instanceof int ss && c instanceof int cc;
    }

    public static boolean narrowingPrimitiveConversion() {
        long l_within_int_range = 42L;
        long l_outside_int_range = 999999999999999999L;

        return l_within_int_range instanceof int lw && !(l_outside_int_range instanceof int lo);
    }

    public static boolean wideningAndNarrowingPrimitiveConversion() {
        byte b = (byte) 42;
        byte b2 = (byte) -42;
        char c = (char) 42;
        return b instanceof char bb && c instanceof byte cc && !(b2 instanceof char b2b);
    }

    public static boolean boxingConversion() {
        int i = 42;

        return i instanceof Integer ii;
    }

    public static boolean boxingAndWideningReferenceConversion() {
        int i = 42;
        return i instanceof Object io &&
                i instanceof Number in &&
                i instanceof Comparable cc;
    }

    public static boolean unboxing() {
        Integer i = Integer.valueOf(1);
        return i instanceof int ii;
    }

    public static boolean unboxingWithObject() {
        Object o1 = (int) 42;
        Object o2 = (byte) 42;

        return o1 instanceof int o1o &&
                o2 instanceof byte o2o &&
                !(o1 instanceof byte o1b &&
                        !(o2 instanceof int o2b ));
    }

    public static <T extends Integer> boolean wideningReferenceConversionUnboxing(T i) {
        return i instanceof int ii;
    }

    public static <T extends Byte> boolean wideningReferenceConversionUnboxing2(T i) {
        return i instanceof byte bb;
    }

    public static <T extends Integer> boolean wideningReferenceConversionUnboxing3(T i) {
        return i instanceof float ff;
    }

    public static <T extends Integer> boolean wideningReferenceConversionUnboxingAndWideningPrimitive(T i) {
        return i instanceof double ii;
    }

    public static boolean unboxingAndWideningPrimitiveExact() {
        Byte b = Byte.valueOf((byte)42);
        Short s = Short.valueOf((short)42);
        Character c = Character.valueOf('a');

        return (b instanceof int bb) && (s instanceof int ss) && (c instanceof int cc);
    }

    public static boolean unboxingAndWideningPrimitiveNotExact() {
        int smallestIntNotRepresentable = 16777217; // 2^24 + 1
        Integer i = Integer.valueOf(smallestIntNotRepresentable);

        return i instanceof float ii;
    }

    public static boolean unboxingWhenNullAndWideningPrimitive() {
        Byte b = null;
        Short s = null;
        Character c = null;

        return !(b instanceof int bb) && !(s instanceof int ss) && !(c instanceof int cc);
    }

    public static boolean narrowingAndUnboxing() {
        Number n = Byte.valueOf((byte) 42);

        return n instanceof byte nn;
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
        return meth() instanceof int ii;
    }

    public class A1 {
        public static int i = 42;
    }
    public static boolean exprStaticallyQualified() {
        return A1.i instanceof int ii;
    }

    static void assertEquals(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }
}
