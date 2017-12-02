/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.ea;

import java.lang.reflect.Field;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

import sun.misc.Unsafe;

/**
 * Exercise a mix of unsafe and normal reads ands writes in situations where EA might attempt to
 * fold the operations.
 */
public class PartialEscapeUnsafeStoreTest extends GraalCompilerTest {

    private static final Unsafe unsafe = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe", e);
        }
    }

    private static final long byteArrayBaseOffset = unsafe.arrayBaseOffset(byte[].class);
    private static byte byteValue = 0x61;

    public static byte[] testByteArrayWithCharStoreSnippet(char v) {
        byte[] b = new byte[8];
        unsafe.putChar(b, byteArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testByteArrayWithCharStore() {
        test("testByteArrayWithCharStoreSnippet", charValue);
    }

    public static byte[] testByteArrayWithShortStoreSnippet(short v) {
        byte[] b = new byte[8];
        unsafe.putShort(b, byteArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testByteArrayWithShortStore() {
        test("testByteArrayWithShortStoreSnippet", shortValue);
    }

    public static byte[] testByteArrayWithIntStoreSnippet(int v) {
        byte[] b = new byte[8];
        unsafe.putInt(b, byteArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testByteArrayWithIntStore() {
        test("testByteArrayWithIntStoreSnippet", intValue);
    }

    public static byte[] testByteArrayWithLongStoreSnippet(long v) {
        byte[] b = new byte[8];
        unsafe.putLong(b, byteArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testByteArrayWithLongStore() {
        test("testByteArrayWithLongStoreSnippet", longValue);
    }

    public static byte[] testByteArrayWithFloatStoreSnippet(float v) {
        byte[] b = new byte[8];
        unsafe.putFloat(b, byteArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testByteArrayWithFloatStore() {
        test("testByteArrayWithFloatStoreSnippet", floatValue);
    }

    public static byte[] testByteArrayWithDoubleStoreSnippet(double v) {
        byte[] b = new byte[8];
        unsafe.putDouble(b, byteArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testByteArrayWithDoubleStore() {
        test("testByteArrayWithDoubleStoreSnippet", doubleValue);
    }

    private static final long charArrayBaseOffset = unsafe.arrayBaseOffset(char[].class);
    private static char charValue = 0x4142;

    public static char[] testCharArrayWithByteStoreSnippet(byte v) {
        char[] b = new char[4];
        unsafe.putByte(b, charArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testCharArrayWithByteStore() {
        test("testCharArrayWithByteStoreSnippet", byteValue);
    }

    public static char[] testCharArrayWithShortStoreSnippet(short v) {
        char[] b = new char[4];
        unsafe.putShort(b, charArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testCharArrayWithShortStore() {
        test("testCharArrayWithShortStoreSnippet", shortValue);
    }

    public static char[] testCharArrayWithIntStoreSnippet(int v) {
        char[] b = new char[4];
        unsafe.putInt(b, charArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testCharArrayWithIntStore() {
        test("testCharArrayWithIntStoreSnippet", intValue);
    }

    public static char[] testCharArrayWithLongStoreSnippet(long v) {
        char[] b = new char[4];
        unsafe.putLong(b, charArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testCharArrayWithLongStore() {
        test("testCharArrayWithLongStoreSnippet", longValue);
    }

    public static char[] testCharArrayWithFloatStoreSnippet(float v) {
        char[] b = new char[4];
        unsafe.putFloat(b, charArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testCharArrayWithFloatStore() {
        test("testCharArrayWithFloatStoreSnippet", floatValue);
    }

    public static char[] testCharArrayWithDoubleStoreSnippet(double v) {
        char[] b = new char[4];
        unsafe.putDouble(b, charArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testCharArrayWithDoubleStore() {
        test("testCharArrayWithDoubleStoreSnippet", doubleValue);
    }

    private static final long shortArrayBaseOffset = unsafe.arrayBaseOffset(short[].class);
    private static short shortValue = 0x1112;

    public static short[] testShortArrayWithByteStoreSnippet(byte v) {
        short[] b = new short[4];
        unsafe.putByte(b, shortArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testShortArrayWithByteStore() {
        test("testShortArrayWithByteStoreSnippet", byteValue);
    }

    public static short[] testShortArrayWithCharStoreSnippet(char v) {
        short[] b = new short[4];
        unsafe.putChar(b, shortArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testShortArrayWithCharStore() {
        test("testShortArrayWithCharStoreSnippet", charValue);
    }

    public static short[] testShortArrayWithIntStoreSnippet(int v) {
        short[] b = new short[4];
        unsafe.putInt(b, shortArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testShortArrayWithIntStore() {
        test("testShortArrayWithIntStoreSnippet", intValue);
    }

    public static short[] testShortArrayWithLongStoreSnippet(long v) {
        short[] b = new short[4];
        unsafe.putLong(b, shortArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testShortArrayWithLongStore() {
        test("testShortArrayWithLongStoreSnippet", longValue);
    }

    public static short[] testShortArrayWithFloatStoreSnippet(float v) {
        short[] b = new short[4];
        unsafe.putFloat(b, shortArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testShortArrayWithFloatStore() {
        test("testShortArrayWithFloatStoreSnippet", floatValue);
    }

    public static short[] testShortArrayWithDoubleStoreSnippet(double v) {
        short[] b = new short[4];
        unsafe.putDouble(b, shortArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testShortArrayWithDoubleStore() {
        test("testShortArrayWithDoubleStoreSnippet", doubleValue);
    }

    private static final long intArrayBaseOffset = unsafe.arrayBaseOffset(int[].class);
    private static int intValue = 0x01020304;

    public static int[] testIntArrayWithByteStoreSnippet(byte v) {
        int[] b = new int[4];
        unsafe.putByte(b, intArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testIntArrayWithByteStore() {
        test("testIntArrayWithByteStoreSnippet", byteValue);
    }

    public static int[] testIntArrayWithCharStoreSnippet(char v) {
        int[] b = new int[4];
        unsafe.putChar(b, intArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testIntArrayWithCharStore() {
        test("testIntArrayWithCharStoreSnippet", charValue);
    }

    public static int[] testIntArrayWithShortStoreSnippet(short v) {
        int[] b = new int[4];
        unsafe.putShort(b, intArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testIntArrayWithShortStore() {
        test("testIntArrayWithShortStoreSnippet", shortValue);
    }

    public static int[] testIntArrayWithLongStoreSnippet(long v) {
        int[] b = new int[4];
        unsafe.putLong(b, intArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testIntArrayWithLongStore() {
        test("testIntArrayWithLongStoreSnippet", longValue);
    }

    public static int[] testIntArrayWithFloatStoreSnippet(float v) {
        int[] b = new int[4];
        unsafe.putFloat(b, intArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testIntArrayWithFloatStore() {
        test("testIntArrayWithFloatStoreSnippet", floatValue);
    }

    public static int[] testIntArrayWithDoubleStoreSnippet(double v) {
        int[] b = new int[4];
        unsafe.putDouble(b, intArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testIntArrayWithDoubleStore() {
        test("testIntArrayWithDoubleStoreSnippet", doubleValue);
    }

    private static final long longArrayBaseOffset = unsafe.arrayBaseOffset(long[].class);
    private static long longValue = 0x31323334353637L;

    public static long[] testLongArrayWithByteStoreSnippet(byte v) {
        long[] b = new long[4];
        unsafe.putByte(b, longArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testLongArrayWithByteStore() {
        test("testLongArrayWithByteStoreSnippet", byteValue);
    }

    public static long[] testLongArrayWithCharStoreSnippet(char v) {
        long[] b = new long[4];
        unsafe.putChar(b, longArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testLongArrayWithCharStore() {
        test("testLongArrayWithCharStoreSnippet", charValue);
    }

    public static long[] testLongArrayWithShortStoreSnippet(short v) {
        long[] b = new long[4];
        unsafe.putShort(b, longArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testLongArrayWithShortStore() {
        test("testLongArrayWithShortStoreSnippet", shortValue);
    }

    public static long[] testLongArrayWithIntStoreSnippet(int v) {
        long[] b = new long[4];
        unsafe.putInt(b, longArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testLongArrayWithIntStore() {
        test("testLongArrayWithIntStoreSnippet", intValue);
    }

    public static long[] testLongArrayWithFloatStoreSnippet(float v) {
        long[] b = new long[4];
        unsafe.putFloat(b, longArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testLongArrayWithFloatStore() {
        test("testLongArrayWithFloatStoreSnippet", floatValue);
    }

    public static long[] testLongArrayWithDoubleStoreSnippet(double v) {
        long[] b = new long[4];
        unsafe.putDouble(b, longArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testLongArrayWithDoubleStore() {
        test("testLongArrayWithDoubleStoreSnippet", doubleValue);
    }

    private static final long floatArrayBaseOffset = unsafe.arrayBaseOffset(float[].class);
    private static float floatValue = Float.NaN;

    public static float[] testFloatArrayWithByteStoreSnippet(byte v) {
        float[] b = new float[4];
        unsafe.putByte(b, floatArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testFloatArrayWithByteStore() {
        test("testFloatArrayWithByteStoreSnippet", byteValue);
    }

    public static float[] testFloatArrayWithCharStoreSnippet(char v) {
        float[] b = new float[4];
        unsafe.putChar(b, floatArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testFloatArrayWithCharStore() {
        test("testFloatArrayWithCharStoreSnippet", charValue);
    }

    public static float[] testFloatArrayWithShortStoreSnippet(short v) {
        float[] b = new float[4];
        unsafe.putShort(b, floatArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testFloatArrayWithShortStore() {
        test("testFloatArrayWithShortStoreSnippet", shortValue);
    }

    public static float[] testFloatArrayWithIntStoreSnippet(int v) {
        float[] b = new float[4];
        unsafe.putInt(b, floatArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testFloatArrayWithIntStore() {
        test("testFloatArrayWithIntStoreSnippet", intValue);
    }

    public static float[] testFloatArrayWithLongStoreSnippet(long v) {
        float[] b = new float[4];
        unsafe.putLong(b, floatArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testFloatArrayWithLongStore() {
        test("testFloatArrayWithLongStoreSnippet", longValue);
    }

    public static float[] testFloatArrayWithDoubleStoreSnippet(double v) {
        float[] b = new float[4];
        unsafe.putDouble(b, floatArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testFloatArrayWithDoubleStore() {
        test("testFloatArrayWithDoubleStoreSnippet", doubleValue);
    }

    private static final long doubleArrayBaseOffset = unsafe.arrayBaseOffset(double[].class);
    private static double doubleValue = Double.NaN;
    private static final int byteSize = 1;
    private static final int charSize = 2;
    private static final int shortSize = 2;
    private static final int intSize = 4;
    private static final int floatSize = 4;
    private static final int longSize = 8;
    private static final int doubleSize = 8;

    public static double[] testDoubleArrayWithByteStoreSnippet(byte v) {
        double[] b = new double[4];
        unsafe.putByte(b, doubleArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testDoubleArrayWithByteStore() {
        test("testDoubleArrayWithByteStoreSnippet", byteValue);
    }

    public static double[] testDoubleArrayWithCharStoreSnippet(char v) {
        double[] b = new double[4];
        unsafe.putChar(b, doubleArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testDoubleArrayWithCharStore() {
        test("testDoubleArrayWithCharStoreSnippet", charValue);
    }

    public static double[] testDoubleArrayWithShortStoreSnippet(short v) {
        double[] b = new double[4];
        unsafe.putShort(b, doubleArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testDoubleArrayWithShortStore() {
        test("testDoubleArrayWithShortStoreSnippet", shortValue);
    }

    public static double[] testDoubleArrayWithIntStoreSnippet(int v) {
        double[] b = new double[4];
        unsafe.putInt(b, doubleArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testDoubleArrayWithIntStore() {
        test("testDoubleArrayWithIntStoreSnippet", intValue);
    }

    public static double[] testDoubleArrayWithLongStoreSnippet(long v) {
        double[] b = new double[4];
        unsafe.putLong(b, doubleArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testDoubleArrayWithLongStore() {
        test("testDoubleArrayWithLongStoreSnippet", longValue);
    }

    public static double[] testDoubleArrayWithFloatStoreSnippet(float v) {
        double[] b = new double[4];
        unsafe.putFloat(b, doubleArrayBaseOffset, v);
        return b;
    }

    @Test
    public void testDoubleArrayWithFloatStore() {
        test("testDoubleArrayWithFloatStoreSnippet", floatValue);
    }

    public static byte testByteArrayWithCharStoreAndReadSnippet(char v) {
        byte[] b = new byte[4];
        unsafe.putChar(b, byteArrayBaseOffset, v);
        return b[(byteSize / charSize) + 1];
    }

    @Test
    public void testByteArrayWithCharStoreAndRead() {
        test("testByteArrayWithCharStoreAndReadSnippet", charValue);
    }

    public static byte testByteArrayWithShortStoreAndReadSnippet(short v) {
        byte[] b = new byte[4];
        unsafe.putShort(b, byteArrayBaseOffset, v);
        return b[(byteSize / shortSize) + 1];
    }

    @Test
    public void testByteArrayWithShortStoreAndRead() {
        test("testByteArrayWithShortStoreAndReadSnippet", shortValue);
    }

    public static byte testByteArrayWithIntStoreAndReadSnippet(int v) {
        byte[] b = new byte[4];
        unsafe.putInt(b, byteArrayBaseOffset, v);
        return b[(byteSize / intSize) + 1];
    }

    @Test
    public void testByteArrayWithIntStoreAndRead() {
        test("testByteArrayWithIntStoreAndReadSnippet", intValue);
    }

    public static byte testByteArrayWithLongStoreAndReadSnippet(long v) {
        byte[] b = new byte[4];
        unsafe.putLong(b, byteArrayBaseOffset, v);
        return b[(byteSize / longSize) + 1];
    }

    @Test
    public void testByteArrayWithLongStoreAndRead() {
        test("testByteArrayWithLongStoreAndReadSnippet", longValue);
    }

    public static byte testByteArrayWithFloatStoreAndReadSnippet(float v) {
        byte[] b = new byte[4];
        unsafe.putFloat(b, byteArrayBaseOffset, v);
        return b[(byteSize / floatSize) + 1];
    }

    @Test
    public void testByteArrayWithFloatStoreAndRead() {
        test("testByteArrayWithFloatStoreAndReadSnippet", floatValue);
    }

    public static byte testByteArrayWithDoubleStoreAndReadSnippet(double v) {
        byte[] b = new byte[4];
        unsafe.putDouble(b, byteArrayBaseOffset, v);
        return b[(byteSize / doubleSize) + 1];
    }

    @Test
    public void testByteArrayWithDoubleStoreAndRead() {
        test("testByteArrayWithDoubleStoreAndReadSnippet", doubleValue);
    }

    public static char testCharArrayWithByteStoreAndReadSnippet(byte v) {
        char[] b = new char[4];
        unsafe.putByte(b, charArrayBaseOffset, v);
        return b[(charSize / byteSize) + 1];
    }

    @Test
    public void testCharArrayWithByteStoreAndRead() {
        test("testCharArrayWithByteStoreAndReadSnippet", byteValue);
    }

    public static char testCharArrayWithShortStoreAndReadSnippet(short v) {
        char[] b = new char[4];
        unsafe.putShort(b, charArrayBaseOffset, v);
        return b[(charSize / shortSize) + 1];
    }

    @Test
    public void testCharArrayWithShortStoreAndRead() {
        test("testCharArrayWithShortStoreAndReadSnippet", shortValue);
    }

    public static char testCharArrayWithIntStoreAndReadSnippet(int v) {
        char[] b = new char[4];
        unsafe.putInt(b, charArrayBaseOffset, v);
        return b[(charSize / intSize) + 1];
    }

    @Test
    public void testCharArrayWithIntStoreAndRead() {
        test("testCharArrayWithIntStoreAndReadSnippet", intValue);
    }

    public static char testCharArrayWithLongStoreAndReadSnippet(long v) {
        char[] b = new char[4];
        unsafe.putLong(b, charArrayBaseOffset, v);
        return b[(charSize / longSize) + 1];
    }

    @Test
    public void testCharArrayWithLongStoreAndRead() {
        test("testCharArrayWithLongStoreAndReadSnippet", longValue);
    }

    public static char testCharArrayWithFloatStoreAndReadSnippet(float v) {
        char[] b = new char[4];
        unsafe.putFloat(b, charArrayBaseOffset, v);
        return b[(charSize / floatSize) + 1];
    }

    @Test
    public void testCharArrayWithFloatStoreAndRead() {
        test("testCharArrayWithFloatStoreAndReadSnippet", floatValue);
    }

    public static char testCharArrayWithDoubleStoreAndReadSnippet(double v) {
        char[] b = new char[4];
        unsafe.putDouble(b, charArrayBaseOffset, v);
        return b[(charSize / doubleSize) + 1];
    }

    @Test
    public void testCharArrayWithDoubleStoreAndRead() {
        test("testCharArrayWithDoubleStoreAndReadSnippet", doubleValue);
    }

    public static short testShortArrayWithByteStoreAndReadSnippet(byte v) {
        short[] b = new short[4];
        unsafe.putByte(b, shortArrayBaseOffset, v);
        return b[(shortSize / byteSize) + 1];
    }

    @Test
    public void testShortArrayWithByteStoreAndRead() {
        test("testShortArrayWithByteStoreAndReadSnippet", byteValue);
    }

    public static short testShortArrayWithCharStoreAndReadSnippet(char v) {
        short[] b = new short[4];
        unsafe.putChar(b, shortArrayBaseOffset, v);
        return b[(shortSize / charSize) + 1];
    }

    @Test
    public void testShortArrayWithCharStoreAndRead() {
        test("testShortArrayWithCharStoreAndReadSnippet", charValue);
    }

    public static short testShortArrayWithIntStoreAndReadSnippet(int v) {
        short[] b = new short[4];
        unsafe.putInt(b, shortArrayBaseOffset, v);
        return b[(shortSize / intSize) + 1];
    }

    @Test
    public void testShortArrayWithIntStoreAndRead() {
        test("testShortArrayWithIntStoreAndReadSnippet", intValue);
    }

    public static short testShortArrayWithLongStoreAndReadSnippet(long v) {
        short[] b = new short[4];
        unsafe.putLong(b, shortArrayBaseOffset, v);
        return b[(shortSize / longSize) + 1];
    }

    @Test
    public void testShortArrayWithLongStoreAndRead() {
        test("testShortArrayWithLongStoreAndReadSnippet", longValue);
    }

    public static short testShortArrayWithFloatStoreAndReadSnippet(float v) {
        short[] b = new short[4];
        unsafe.putFloat(b, shortArrayBaseOffset, v);
        return b[(shortSize / floatSize) + 1];
    }

    @Test
    public void testShortArrayWithFloatStoreAndRead() {
        test("testShortArrayWithFloatStoreAndReadSnippet", floatValue);
    }

    public static short testShortArrayWithDoubleStoreAndReadSnippet(double v) {
        short[] b = new short[4];
        unsafe.putDouble(b, shortArrayBaseOffset, v);
        return b[(shortSize / doubleSize) + 1];
    }

    @Test
    public void testShortArrayWithDoubleStoreAndRead() {
        test("testShortArrayWithDoubleStoreAndReadSnippet", doubleValue);
    }

    public static int testIntArrayWithByteStoreAndReadSnippet(byte v) {
        int[] b = new int[4];
        unsafe.putByte(b, intArrayBaseOffset, v);
        return b[(intSize / byteSize) + 1];
    }

    @Test
    public void testIntArrayWithByteStoreAndRead() {
        test("testIntArrayWithByteStoreAndReadSnippet", byteValue);
    }

    public static int testIntArrayWithCharStoreAndReadSnippet(char v) {
        int[] b = new int[4];
        unsafe.putChar(b, intArrayBaseOffset, v);
        return b[(intSize / charSize) + 1];
    }

    @Test
    public void testIntArrayWithCharStoreAndRead() {
        test("testIntArrayWithCharStoreAndReadSnippet", charValue);
    }

    public static int testIntArrayWithShortStoreAndReadSnippet(short v) {
        int[] b = new int[4];
        unsafe.putShort(b, intArrayBaseOffset, v);
        return b[(intSize / shortSize) + 1];
    }

    @Test
    public void testIntArrayWithShortStoreAndRead() {
        test("testIntArrayWithShortStoreAndReadSnippet", shortValue);
    }

    public static int testIntArrayWithLongStoreAndReadSnippet(long v) {
        int[] b = new int[4];
        unsafe.putLong(b, intArrayBaseOffset, v);
        return b[(intSize / longSize) + 1];
    }

    @Test
    public void testIntArrayWithLongStoreAndRead() {
        test("testIntArrayWithLongStoreAndReadSnippet", longValue);
    }

    public static int testIntArrayWithFloatStoreAndReadSnippet(float v) {
        int[] b = new int[4];
        unsafe.putFloat(b, intArrayBaseOffset, v);
        return b[(intSize / floatSize) + 1];
    }

    @Test
    public void testIntArrayWithFloatStoreAndRead() {
        test("testIntArrayWithFloatStoreAndReadSnippet", floatValue);
    }

    public static int testIntArrayWithDoubleStoreAndReadSnippet(double v) {
        int[] b = new int[4];
        unsafe.putDouble(b, intArrayBaseOffset, v);
        return b[(intSize / doubleSize) + 1];
    }

    @Test
    public void testIntArrayWithDoubleStoreAndRead() {
        test("testIntArrayWithDoubleStoreAndReadSnippet", doubleValue);
    }

    public static long testLongArrayWithByteStoreAndReadSnippet(byte v) {
        long[] b = new long[4];
        unsafe.putByte(b, longArrayBaseOffset, v);
        return b[(longSize / byteSize) + 1];
    }

    @Test
    public void testLongArrayWithByteStoreAndRead() {
        test("testLongArrayWithByteStoreAndReadSnippet", byteValue);
    }

    public static long testLongArrayWithCharStoreAndReadSnippet(char v) {
        long[] b = new long[4];
        unsafe.putChar(b, longArrayBaseOffset, v);
        return b[(longSize / charSize) + 1];
    }

    @Test
    public void testLongArrayWithCharStoreAndRead() {
        test("testLongArrayWithCharStoreAndReadSnippet", charValue);
    }

    public static long testLongArrayWithShortStoreAndReadSnippet(short v) {
        long[] b = new long[4];
        unsafe.putShort(b, longArrayBaseOffset, v);
        return b[(longSize / shortSize) + 1];
    }

    @Test
    public void testLongArrayWithShortStoreAndRead() {
        test("testLongArrayWithShortStoreAndReadSnippet", shortValue);
    }

    public static long testLongArrayWithIntStoreAndReadSnippet(int v) {
        long[] b = new long[4];
        unsafe.putInt(b, longArrayBaseOffset, v);
        return b[(longSize / intSize) + 1];
    }

    @Test
    public void testLongArrayWithIntStoreAndRead() {
        test("testLongArrayWithIntStoreAndReadSnippet", intValue);
    }

    public static long testLongArrayWithFloatStoreAndReadSnippet(float v) {
        long[] b = new long[4];
        unsafe.putFloat(b, longArrayBaseOffset, v);
        return b[(longSize / floatSize) + 1];
    }

    @Test
    public void testLongArrayWithFloatStoreAndRead() {
        test("testLongArrayWithFloatStoreAndReadSnippet", floatValue);
    }

    public static long testLongArrayWithDoubleStoreAndReadSnippet(double v) {
        long[] b = new long[4];
        unsafe.putDouble(b, longArrayBaseOffset, v);
        return b[(longSize / doubleSize) + 1];
    }

    @Test
    public void testLongArrayWithDoubleStoreAndRead() {
        test("testLongArrayWithDoubleStoreAndReadSnippet", doubleValue);
    }

    public static float testFloatArrayWithByteStoreAndReadSnippet(byte v) {
        float[] b = new float[4];
        unsafe.putByte(b, floatArrayBaseOffset, v);
        return b[(floatSize / byteSize) + 1];
    }

    @Test
    public void testFloatArrayWithByteStoreAndRead() {
        test("testFloatArrayWithByteStoreAndReadSnippet", byteValue);
    }

    public static float testFloatArrayWithCharStoreAndReadSnippet(char v) {
        float[] b = new float[4];
        unsafe.putChar(b, floatArrayBaseOffset, v);
        return b[(floatSize / charSize) + 1];
    }

    @Test
    public void testFloatArrayWithCharStoreAndRead() {
        test("testFloatArrayWithCharStoreAndReadSnippet", charValue);
    }

    public static float testFloatArrayWithShortStoreAndReadSnippet(short v) {
        float[] b = new float[4];
        unsafe.putShort(b, floatArrayBaseOffset, v);
        return b[(floatSize / shortSize) + 1];
    }

    @Test
    public void testFloatArrayWithShortStoreAndRead() {
        test("testFloatArrayWithShortStoreAndReadSnippet", shortValue);
    }

    public static float testFloatArrayWithIntStoreAndReadSnippet(int v) {
        float[] b = new float[4];
        unsafe.putInt(b, floatArrayBaseOffset, v);
        return b[(floatSize / intSize) + 1];
    }

    @Test
    public void testFloatArrayWithIntStoreAndRead() {
        test("testFloatArrayWithIntStoreAndReadSnippet", intValue);
    }

    public static float testFloatArrayWithLongStoreAndReadSnippet(long v) {
        float[] b = new float[4];
        unsafe.putLong(b, floatArrayBaseOffset, v);
        return b[(floatSize / longSize) + 1];
    }

    @Test
    public void testFloatArrayWithLongStoreAndRead() {
        test("testFloatArrayWithLongStoreAndReadSnippet", longValue);
    }

    public static float testFloatArrayWithDoubleStoreAndReadSnippet(double v) {
        float[] b = new float[4];
        unsafe.putDouble(b, floatArrayBaseOffset, v);
        return b[(floatSize / doubleSize) + 1];
    }

    @Test
    public void testFloatArrayWithDoubleStoreAndRead() {
        test("testFloatArrayWithDoubleStoreAndReadSnippet", doubleValue);
    }

    public static double testDoubleArrayWithByteStoreAndReadSnippet(byte v) {
        double[] b = new double[4];
        unsafe.putByte(b, doubleArrayBaseOffset, v);
        return b[(doubleSize / byteSize) + 1];
    }

    @Test
    public void testDoubleArrayWithByteStoreAndRead() {
        test("testDoubleArrayWithByteStoreAndReadSnippet", byteValue);
    }

    public static double testDoubleArrayWithCharStoreAndReadSnippet(char v) {
        double[] b = new double[4];
        unsafe.putChar(b, doubleArrayBaseOffset, v);
        return b[(doubleSize / charSize) + 1];
    }

    @Test
    public void testDoubleArrayWithCharStoreAndRead() {
        test("testDoubleArrayWithCharStoreAndReadSnippet", charValue);
    }

    public static double testDoubleArrayWithShortStoreAndReadSnippet(short v) {
        double[] b = new double[4];
        unsafe.putShort(b, doubleArrayBaseOffset, v);
        return b[(doubleSize / shortSize) + 1];
    }

    @Test
    public void testDoubleArrayWithShortStoreAndRead() {
        test("testDoubleArrayWithShortStoreAndReadSnippet", shortValue);
    }

    public static double testDoubleArrayWithIntStoreAndReadSnippet(int v) {
        double[] b = new double[4];
        unsafe.putInt(b, doubleArrayBaseOffset, v);
        return b[(doubleSize / intSize) + 1];
    }

    @Test
    public void testDoubleArrayWithIntStoreAndRead() {
        test("testDoubleArrayWithIntStoreAndReadSnippet", intValue);
    }

    public static double testDoubleArrayWithLongStoreAndReadSnippet(long v) {
        double[] b = new double[4];
        unsafe.putLong(b, doubleArrayBaseOffset, v);
        return b[(doubleSize / longSize) + 1];
    }

    @Test
    public void testDoubleArrayWithLongStoreAndRead() {
        test("testDoubleArrayWithLongStoreAndReadSnippet", longValue);
    }

    public static double testDoubleArrayWithFloatStoreAndReadSnippet(float v) {
        double[] b = new double[4];
        unsafe.putFloat(b, doubleArrayBaseOffset, v);
        return b[(doubleSize / floatSize) + 1];
    }

    @Test
    public void testDoubleArrayWithFloatStoreAndRead() {
        test("testDoubleArrayWithFloatStoreAndReadSnippet", floatValue);
    }
}
