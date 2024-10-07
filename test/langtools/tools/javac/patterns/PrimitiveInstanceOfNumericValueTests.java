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
 * @bug 8304487
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Preview)
 * @enablePreview
 * @compile PrimitiveInstanceOfNumericValueTests.java
 * @run main/othervm PrimitiveInstanceOfNumericValueTests
 */
public class PrimitiveInstanceOfNumericValueTests {

    public static void main(String[] args) {
        testByte();
        testShort();
        testChar();
        testInt();
        testLong();
        testFloat();
        testDouble();
    }

    public static void testByte() {
        assertEquals(true,  ((byte) (Byte.MAX_VALUE)) instanceof byte);
        assertEquals(true,  ((byte) (0)) instanceof byte);
        assertEquals(true,  ((byte) (Byte.MIN_VALUE)) instanceof byte);
        assertEquals(false, ((short) (Short.MAX_VALUE)) instanceof byte);
        assertEquals(true,  ((short) (0)) instanceof byte);
        assertEquals(false, ((short) (Short.MIN_VALUE)) instanceof byte);
        assertEquals(false, ((char) (Character.MAX_VALUE)) instanceof byte);
        assertEquals(true,  ((char) (Character.MIN_VALUE)) instanceof byte);
        assertEquals(false, (Integer.MAX_VALUE) instanceof byte);
        assertEquals(true,  (0) instanceof byte);
        assertEquals(false, (Integer.MIN_VALUE) instanceof byte);
        assertEquals(false, (Long.MAX_VALUE) instanceof byte);
        assertEquals(true,  (0L) instanceof byte);
        assertEquals(false, (Long.MIN_VALUE) instanceof byte);
        assertEquals(false, (Float.MAX_VALUE) instanceof byte);
        assertEquals(true,  ((float) 0) instanceof byte);
        assertEquals(false, (Float.MIN_VALUE) instanceof byte);
        assertEquals(false, (Float.NaN) instanceof byte);
        assertEquals(false, (Float.POSITIVE_INFINITY) instanceof byte);
        assertEquals(false, (Float.NEGATIVE_INFINITY) instanceof byte);
        assertEquals(false, (-0.0f) instanceof byte);
        assertEquals(true,  (+0.0f) instanceof byte);
        assertEquals(false, (Double.MAX_VALUE) instanceof byte);
        assertEquals(true,  ((double) 0) instanceof byte);
        assertEquals(false, (Double.MIN_VALUE) instanceof byte);
        assertEquals(false, (Double.NaN) instanceof byte);
        assertEquals(false, (Double.POSITIVE_INFINITY) instanceof byte);
        assertEquals(false, (Double.NEGATIVE_INFINITY) instanceof byte);
        assertEquals(false, (-0.0d) instanceof byte);
        assertEquals(true,  (+0.0d) instanceof byte);
    }
    public static void testShort() {
        assertEquals(true, ((byte) (Byte.MAX_VALUE)) instanceof short);
        assertEquals(true, ((byte) (0)) instanceof short);
        assertEquals(true, ((byte) (Byte.MIN_VALUE)) instanceof short);
        assertEquals(true, ((short) (Short.MAX_VALUE)) instanceof short);
        assertEquals(true, ((short) (0)) instanceof short);
        assertEquals(true, ((short) (Short.MIN_VALUE)) instanceof short);
        assertEquals(false, ((char) (Character.MAX_VALUE)) instanceof short);
        assertEquals(true, ((char) (Character.MIN_VALUE)) instanceof short);
        assertEquals(false, (Integer.MAX_VALUE) instanceof short);
        assertEquals(true, (0) instanceof short);
        assertEquals(false, (Integer.MIN_VALUE) instanceof short);
        assertEquals(false, (Long.MAX_VALUE) instanceof short);
        assertEquals(true, (0L) instanceof short);
        assertEquals(false, (Long.MIN_VALUE) instanceof short);
        assertEquals(false, (Float.MAX_VALUE) instanceof short);
        assertEquals(true, ((float) 0) instanceof short);
        assertEquals(false, (Float.MIN_VALUE) instanceof short);
        assertEquals(false, (Float.MIN_VALUE) instanceof short);
        assertEquals(false, (Float.NaN) instanceof short);
        assertEquals(false, (Float.POSITIVE_INFINITY) instanceof short);
        assertEquals(false, (Float.NEGATIVE_INFINITY) instanceof short);
        assertEquals(false, (-0.0f) instanceof short);
        assertEquals(true, (+0.0f) instanceof short);
        assertEquals(false, (Double.MAX_VALUE) instanceof short);
        assertEquals(true, ((double) 0) instanceof short);
        assertEquals(false, (Double.MIN_VALUE) instanceof short);
        assertEquals(false, (Double.NaN) instanceof short);
        assertEquals(false, (Double.POSITIVE_INFINITY) instanceof short);
        assertEquals(false, (Double.NEGATIVE_INFINITY) instanceof short);
        assertEquals(false, (-0.0d) instanceof short);
        assertEquals(true, (+0.0d) instanceof short);
    }
    public static void testChar() {
        assertEquals(true, ((byte) (Byte.MAX_VALUE)) instanceof char);
        assertEquals(true, ((byte) (0)) instanceof char);
        assertEquals(false, ((byte) (Byte.MIN_VALUE)) instanceof char);
        assertEquals(true, ((short) (Short.MAX_VALUE)) instanceof char);
        assertEquals(true, ((short) (0)) instanceof char);
        assertEquals(false, ((short) (Short.MIN_VALUE)) instanceof char);
        assertEquals(true, ((char) (Character.MAX_VALUE)) instanceof char);
        assertEquals(true, ((char) (Character.MIN_VALUE)) instanceof char);
        assertEquals(false, (Integer.MAX_VALUE) instanceof char);
        assertEquals(true, (0) instanceof char);
        assertEquals(false, (Integer.MIN_VALUE) instanceof char);
        assertEquals(false, (Long.MAX_VALUE) instanceof char);
        assertEquals(true, (0L) instanceof char);
        assertEquals(false, (Long.MIN_VALUE) instanceof char);
        assertEquals(false, (Float.MAX_VALUE) instanceof char);
        assertEquals(true, ((float) 0) instanceof char);
        assertEquals(false, (Float.MIN_VALUE) instanceof char);
        assertEquals(false, (Float.NaN) instanceof char);
        assertEquals(false, (Float.POSITIVE_INFINITY) instanceof char);
        assertEquals(false, (Float.NEGATIVE_INFINITY) instanceof char);
        assertEquals(false, (-0.0f) instanceof char);
        assertEquals(true, (+0.0f) instanceof char);
        assertEquals(false, (Double.MAX_VALUE) instanceof char);
        assertEquals(true, ((double) 0) instanceof char);
        assertEquals(false, (Double.MIN_VALUE) instanceof char);
        assertEquals(false, (Double.NaN) instanceof char);
        assertEquals(false, (Double.POSITIVE_INFINITY) instanceof char);
        assertEquals(false, (Double.NEGATIVE_INFINITY) instanceof char);
        assertEquals(false, (-0.0d) instanceof char);
        assertEquals(true, (+0.0d) instanceof char);
    }
    public static void testInt() {
        assertEquals(true, ((byte) (Byte.MAX_VALUE)) instanceof int);
        assertEquals(true, ((byte) (0)) instanceof int);
        assertEquals(true, ((byte) (Byte.MIN_VALUE)) instanceof int);
        assertEquals(true, ((short) (Short.MAX_VALUE)) instanceof int);
        assertEquals(true, ((short) (0)) instanceof int);
        assertEquals(true, ((short) (Short.MIN_VALUE)) instanceof int);
        assertEquals(true, ((char) (Character.MAX_VALUE)) instanceof int);
        assertEquals(true, ((char) (Character.MIN_VALUE)) instanceof int);
        assertEquals(true, (Integer.MAX_VALUE) instanceof int);
        assertEquals(true, (0) instanceof int);
        assertEquals(true, (Integer.MIN_VALUE) instanceof int);
        assertEquals(false, (Long.MAX_VALUE) instanceof int);
        assertEquals(true, (0L) instanceof int);
        assertEquals(false, (Long.MIN_VALUE) instanceof int);
        assertEquals(false, (Float.MAX_VALUE) instanceof int);
        assertEquals(true, ((float) 0) instanceof int);
        assertEquals(false, (Float.MIN_VALUE) instanceof int);
        assertEquals(false, (Float.NaN) instanceof int);
        assertEquals(false, (Float.POSITIVE_INFINITY) instanceof int);
        assertEquals(false, (Float.NEGATIVE_INFINITY) instanceof int);
        assertEquals(false, (-0.0f) instanceof int);
        assertEquals(true, (+0.0f) instanceof int);
        assertEquals(false, (Double.MAX_VALUE) instanceof int);
        assertEquals(true, ((double) 0) instanceof int);
        assertEquals(false, (Double.MIN_VALUE) instanceof int);
        assertEquals(false, (Double.NaN) instanceof int);
        assertEquals(false, (Double.POSITIVE_INFINITY) instanceof int);
        assertEquals(false, (Double.NEGATIVE_INFINITY) instanceof int);
        assertEquals(false, (-0.0d) instanceof int);
        assertEquals(true, (+0.0d) instanceof int);
    }
    public static void testLong() {
        assertEquals(true, ((byte) (Byte.MAX_VALUE)) instanceof long);
        assertEquals(true, ((byte) (0)) instanceof long);
        assertEquals(true, ((byte) (Byte.MIN_VALUE)) instanceof long);
        assertEquals(true, ((short) (Short.MAX_VALUE)) instanceof long);
        assertEquals(true, ((short) (0)) instanceof long);
        assertEquals(true, ((short) (Short.MIN_VALUE)) instanceof long);
        assertEquals(true, ((char) (Character.MAX_VALUE)) instanceof long);
        assertEquals(true, ((char) (Character.MIN_VALUE)) instanceof long);
        assertEquals(true, (Integer.MAX_VALUE) instanceof long);
        assertEquals(true, (0L) instanceof long);
        assertEquals(true, (Integer.MIN_VALUE) instanceof long);
        assertEquals(true, (Long.MAX_VALUE) instanceof long);
        assertEquals(true, (0) instanceof long);
        assertEquals(true, (Long.MIN_VALUE) instanceof long);
        assertEquals(false, (Float.MAX_VALUE) instanceof long);
        assertEquals(true, ((float) 0) instanceof long);
        assertEquals(false, (Float.MIN_VALUE) instanceof long);
        assertEquals(false, (Float.NaN) instanceof long);
        assertEquals(false, (Float.POSITIVE_INFINITY) instanceof long);
        assertEquals(false, (Float.NEGATIVE_INFINITY) instanceof long);
        assertEquals(false, (-0.0f) instanceof long);
        assertEquals(true, (+0.0f) instanceof long);
        assertEquals(false, (Double.MAX_VALUE) instanceof long);
        assertEquals(true, ((double) 0) instanceof long);
        assertEquals(false, (Double.MIN_VALUE) instanceof long);
        assertEquals(false, (Double.NaN) instanceof long);
        assertEquals(false, (Double.POSITIVE_INFINITY) instanceof long);
        assertEquals(false, (Double.NEGATIVE_INFINITY) instanceof long);
        assertEquals(false, (-0.0d) instanceof long);
        assertEquals(true, (+0.0d) instanceof long);
    }
    public static void testFloat() {
        assertEquals(true, ((byte) (Byte.MAX_VALUE)) instanceof float);
        assertEquals(true, ((byte) (0) instanceof float));
        assertEquals(true, ((byte) (Byte.MIN_VALUE)) instanceof float);
        assertEquals(true, ((short) (Short.MAX_VALUE)) instanceof float);
        assertEquals(true, ((short) (0)) instanceof float);
        assertEquals(true, ((short) (Short.MIN_VALUE)) instanceof float);
        assertEquals(true, ((char) (Character.MAX_VALUE)) instanceof float);
        assertEquals(true, ((char) (Character.MIN_VALUE)) instanceof float);
        assertEquals(false, (Integer.MAX_VALUE) instanceof float);
        assertEquals(true, (0) instanceof float);
        assertEquals(true, (Integer.MIN_VALUE) instanceof float);
        assertEquals(false, (Long.MAX_VALUE) instanceof float);
        assertEquals(true, (0L) instanceof float);
        assertEquals(true, (Long.MIN_VALUE) instanceof float);
        assertEquals(true, (Float.MAX_VALUE) instanceof float);
        assertEquals(true, ((float) 0) instanceof float);
        assertEquals(true, (Float.MIN_VALUE) instanceof float);
        assertEquals(true, (Float.NaN) instanceof float);
        assertEquals(true, (Float.POSITIVE_INFINITY) instanceof float);
        assertEquals(true, (Float.NEGATIVE_INFINITY) instanceof float);
        assertEquals(true, (-0.0f) instanceof float);
        assertEquals(true, (+0.0f) instanceof float);
        assertEquals(false, (Double.MAX_VALUE) instanceof float);
        assertEquals(true, ((double) 0) instanceof float);
        assertEquals(false, (Double.MIN_VALUE) instanceof float);
        assertEquals(true, (Double.NaN) instanceof float);
        assertEquals(true, (Double.POSITIVE_INFINITY) instanceof float);
        assertEquals(true, (Double.NEGATIVE_INFINITY) instanceof float);
        assertEquals(true, (-0.0d) instanceof float);
        assertEquals(true, (+0.0d) instanceof float);
    }
    public static void testDouble() {
        assertEquals(true, ((byte) (Byte.MAX_VALUE)) instanceof double);
        assertEquals(true, ((byte) (0)) instanceof double);
        assertEquals(true, ((byte) (Byte.MIN_VALUE)) instanceof double);
        assertEquals(true, ((short) (Short.MAX_VALUE)) instanceof double);
        assertEquals(true, ((short) (0)) instanceof double);
        assertEquals(true, ((short) (Short.MIN_VALUE)) instanceof double);
        assertEquals(true, ((char) (Character.MAX_VALUE)) instanceof double);
        assertEquals(true, ((char) (Character.MIN_VALUE)) instanceof double);
        assertEquals(true, (Integer.MAX_VALUE) instanceof double);
        assertEquals(true, (0) instanceof double);
        assertEquals(true, (Integer.MIN_VALUE) instanceof double);
        assertEquals(false, (Long.MAX_VALUE) instanceof double);
        assertEquals(true, (0L) instanceof double);
        assertEquals(true, (Long.MIN_VALUE) instanceof double);
        assertEquals(true, (Float.MAX_VALUE) instanceof double);
        assertEquals(true, ((float) 0) instanceof double);
        assertEquals(true, (Float.MIN_VALUE) instanceof double);
        assertEquals(true, (Float.NaN) instanceof double);
        assertEquals(true, (Float.POSITIVE_INFINITY) instanceof double);
        assertEquals(true, (Float.NEGATIVE_INFINITY) instanceof double);
        assertEquals(true, (-0.0f) instanceof double);
        assertEquals(true, (+0.0f) instanceof double);
        assertEquals(true, (Double.MAX_VALUE) instanceof double);
        assertEquals(true, ((double) 0) instanceof double);
        assertEquals(true, (Double.MIN_VALUE) instanceof double);
        assertEquals(true, (Double.NaN) instanceof double);
        assertEquals(true, (Double.POSITIVE_INFINITY) instanceof double);
        assertEquals(true, (Double.NEGATIVE_INFINITY) instanceof double);
        assertEquals(true, (-0.0d) instanceof double);
        assertEquals(true, (+0.0d) instanceof double);
    }

    static void assertEquals(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }
}
