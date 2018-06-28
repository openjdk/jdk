/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.core.test.GraalCompilerTest;

/**
 * Tests the implementation of {@code [A]NEWARRAY}.
 */
public class NewArrayTest extends GraalCompilerTest {

    @Override
    protected void assertDeepEquals(Object expected, Object actual) {
        Assert.assertTrue(expected != null);
        Assert.assertTrue(actual != null);
        super.assertDeepEquals(expected.getClass(), actual.getClass());
        if (expected instanceof int[]) {
            Assert.assertArrayEquals((int[]) expected, (int[]) actual);
        } else if (expected instanceof byte[]) {
            Assert.assertArrayEquals((byte[]) expected, (byte[]) actual);
        } else if (expected instanceof char[]) {
            Assert.assertArrayEquals((char[]) expected, (char[]) actual);
        } else if (expected instanceof short[]) {
            Assert.assertArrayEquals((short[]) expected, (short[]) actual);
        } else if (expected instanceof float[]) {
            Assert.assertArrayEquals((float[]) expected, (float[]) actual, 0.0f);
        } else if (expected instanceof long[]) {
            Assert.assertArrayEquals((long[]) expected, (long[]) actual);
        } else if (expected instanceof double[]) {
            Assert.assertArrayEquals((double[]) expected, (double[]) actual, 0.0d);
        } else if (expected instanceof Object[]) {
            Assert.assertArrayEquals((Object[]) expected, (Object[]) actual);
        } else {
            Assert.fail("non-array value encountered: " + expected);
        }
    }

    @Test
    public void test1() {
        for (String type : new String[]{"Byte", "Char", "Short", "Int", "Float", "Long", "Double", "String"}) {
            test("new" + type + "Array7");
            test("new" + type + "ArrayMinus7");
            test("new" + type + "Array", 7);
            test("new" + type + "Array", -7);
            test("new" + type + "Array", Integer.MAX_VALUE);
            test("new" + type + "Array", Integer.MIN_VALUE);
        }
    }

    public static Object newCharArray7() {
        return new char[7];
    }

    public static Object newCharArrayMinus7() {
        return new char[-7];
    }

    public static Object newCharArray(int length) {
        return new char[length];
    }

    public static Object newShortArray7() {
        return new short[7];
    }

    public static Object newShortArrayMinus7() {
        return new short[-7];
    }

    public static Object newShortArray(int length) {
        return new short[length];
    }

    public static Object newFloatArray7() {
        return new float[7];
    }

    public static Object newFloatArrayMinus7() {
        return new float[-7];
    }

    public static Object newFloatArray(int length) {
        return new float[length];
    }

    public static Object newLongArray7() {
        return new long[7];
    }

    public static Object newLongArrayMinus7() {
        return new long[-7];
    }

    public static Object newLongArray(int length) {
        return new long[length];
    }

    public static Object newDoubleArray7() {
        return new double[7];
    }

    public static Object newDoubleArrayMinus7() {
        return new double[-7];
    }

    public static Object newDoubleArray(int length) {
        return new double[length];
    }

    public static Object newIntArray7() {
        return new int[7];
    }

    public static Object newIntArrayMinus7() {
        return new int[-7];
    }

    public static Object newIntArray(int length) {
        return new int[length];
    }

    public static Object newByteArray7() {
        return new byte[7];
    }

    public static Object newByteArrayMinus7() {
        return new byte[-7];
    }

    public static Object newByteArray(int length) {
        return new byte[length];
    }

    public static Object newStringArray7() {
        return new String[7];
    }

    public static Object newStringArrayMinus7() {
        return new String[-7];
    }

    public static Object newStringArray(int length) {
        return new String[length];
    }
}
