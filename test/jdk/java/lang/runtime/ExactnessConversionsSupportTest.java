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

import org.testng.annotations.Test;

import java.io.Serializable;
import java.lang.Enum.EnumDesc;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.runtime.ExactConversionsSupport;
import java.lang.runtime.SwitchBootstraps;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;

/**
 * @test
 * @bug 8304487
 * @summary Verify boundary and special cases of exact conversion predicates
 * @enablePreview
 * @modules java.base/jdk.internal.classfile
 * @compile ExactnessConversionsSupportTest.java
 * @run testng/othervm ExactnessConversionsSupportTest
 */
@Test
public class ExactnessConversionsSupportTest {

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
        assertEquals(true,  ExactConversionsSupport.isIntToByteExact((byte) (Byte.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToByteExact((byte) (0)));
        assertEquals(true,  ExactConversionsSupport.isIntToByteExact((byte) (Byte.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isIntToByteExact((short) (Short.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToByteExact((short) (0)));
        assertEquals(false, ExactConversionsSupport.isIntToByteExact((short) (Short.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isIntToByteExact((char) (Character.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToByteExact((char) (Character.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isIntToByteExact(Integer.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isIntToByteExact(0));
        assertEquals(false, ExactConversionsSupport.isIntToByteExact(Integer.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isLongToByteExact(Long.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isLongToByteExact(0L));
        assertEquals(false, ExactConversionsSupport.isLongToByteExact(Long.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isFloatToByteExact(Float.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isFloatToByteExact((float) 0));
        assertEquals(false, ExactConversionsSupport.isFloatToByteExact(Float.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isFloatToByteExact(Float.NaN));
        assertEquals(false, ExactConversionsSupport.isFloatToByteExact(Float.POSITIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isFloatToByteExact(Float.NEGATIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isFloatToByteExact(-0.0f));
        assertEquals(true,  ExactConversionsSupport.isFloatToByteExact(+0.0f));
        assertEquals(false, ExactConversionsSupport.isDoubleToByteExact(Double.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isDoubleToByteExact(0d));
        assertEquals(false, ExactConversionsSupport.isDoubleToByteExact(Double.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isDoubleToByteExact(Double.NaN));
        assertEquals(false, ExactConversionsSupport.isDoubleToByteExact(Double.POSITIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isDoubleToByteExact(Double.NEGATIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isDoubleToByteExact(-0.0d));
        assertEquals(true,  ExactConversionsSupport.isDoubleToByteExact(+0.0d));
    }
    public static void testShort() {
        assertEquals(true,  ExactConversionsSupport.isIntToShortExact((byte) (Byte.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToShortExact((byte) (0)));
        assertEquals(true,  ExactConversionsSupport.isIntToShortExact((byte) (Byte.MIN_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToShortExact((short) (Short.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToShortExact((short) (0)));
        assertEquals(true,  ExactConversionsSupport.isIntToShortExact((short) (Short.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isIntToShortExact((char) (Character.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToShortExact((char) (Character.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isIntToShortExact((Integer.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToShortExact((0)));
        assertEquals(false, ExactConversionsSupport.isIntToShortExact((Integer.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isLongToShortExact(Long.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isLongToShortExact(0L));
        assertEquals(false, ExactConversionsSupport.isLongToShortExact(Long.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isFloatToShortExact(Float.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isFloatToShortExact(0f));
        assertEquals(false, ExactConversionsSupport.isFloatToShortExact(Float.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isFloatToShortExact(Float.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isFloatToShortExact(Float.NaN));
        assertEquals(false, ExactConversionsSupport.isFloatToShortExact(Float.POSITIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isFloatToShortExact(Float.NEGATIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isFloatToShortExact(-0.0f));
        assertEquals(true,  ExactConversionsSupport.isFloatToShortExact(+0.0f));
        assertEquals(false, ExactConversionsSupport.isDoubleToShortExact(Double.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isDoubleToShortExact((double) 0));
        assertEquals(false, ExactConversionsSupport.isDoubleToShortExact(Double.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isDoubleToShortExact(Double.NaN));
        assertEquals(false, ExactConversionsSupport.isDoubleToShortExact(Double.POSITIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isDoubleToShortExact(Double.NEGATIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isDoubleToShortExact(-0.0d));
        assertEquals(true,  ExactConversionsSupport.isDoubleToShortExact(+0.0d));
    }
    public static void testChar() {
        assertEquals(true,  ExactConversionsSupport.isIntToCharExact((byte) (Byte.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToCharExact((byte) (0)));
        assertEquals(false, ExactConversionsSupport.isIntToCharExact((byte) (Byte.MIN_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToCharExact((short) (Short.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToCharExact((short) (0)));
        assertEquals(false, ExactConversionsSupport.isIntToCharExact((short) (Short.MIN_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToCharExact((char) (Character.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToCharExact((char) (Character.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isIntToCharExact (Integer.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isIntToCharExact(0));
        assertEquals(false, ExactConversionsSupport.isIntToCharExact(Integer.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isLongToCharExact(Long.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isLongToCharExact(0l));
        assertEquals(false, ExactConversionsSupport.isLongToCharExact(Long.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isFloatToCharExact(Float.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isFloatToCharExact((float) 0));
        assertEquals(false, ExactConversionsSupport.isFloatToCharExact(Float.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isFloatToCharExact(Float.NaN));
        assertEquals(false, ExactConversionsSupport.isFloatToCharExact(Float.POSITIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isFloatToCharExact(Float.NEGATIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isFloatToCharExact(-0.0f));
        assertEquals(true,  ExactConversionsSupport.isFloatToCharExact(+0.0f));
        assertEquals(false, ExactConversionsSupport.isDoubleToCharExact(Double.MAX_VALUE));
        assertEquals(true,  ExactConversionsSupport.isDoubleToCharExact((double) 0));
        assertEquals(false, ExactConversionsSupport.isDoubleToCharExact(Double.MIN_VALUE));
        assertEquals(false, ExactConversionsSupport.isDoubleToCharExact(Double.NaN));
        assertEquals(false, ExactConversionsSupport.isDoubleToCharExact(Double.POSITIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isDoubleToCharExact(Double.NEGATIVE_INFINITY));
        assertEquals(false, ExactConversionsSupport.isDoubleToCharExact(-0.0d));
        assertEquals(true,  ExactConversionsSupport.isDoubleToCharExact(+0.0d));
    }
    public static void testInt() {
        assertEquals(false, ExactConversionsSupport.isLongToIntExact((Long.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isLongToIntExact((0L)));
        assertEquals(false, ExactConversionsSupport.isLongToIntExact((Long.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isFloatToIntExact((Float.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isFloatToIntExact(((float) 0)));
        assertEquals(false, ExactConversionsSupport.isFloatToIntExact((Float.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isFloatToIntExact((Float.NaN)));
        assertEquals(false, ExactConversionsSupport.isFloatToIntExact((Float.POSITIVE_INFINITY)));
        assertEquals(false, ExactConversionsSupport.isFloatToIntExact((Float.NEGATIVE_INFINITY)));
        assertEquals(false, ExactConversionsSupport.isFloatToIntExact((-0.0f)));
        assertEquals(true,  ExactConversionsSupport.isFloatToIntExact((+0.0f)));
        assertEquals(false, ExactConversionsSupport.isDoubleToIntExact((Double.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isDoubleToIntExact(((double) 0)));
        assertEquals(false, ExactConversionsSupport.isDoubleToIntExact((Double.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isDoubleToIntExact((Double.NaN)));
        assertEquals(false, ExactConversionsSupport.isDoubleToIntExact((Double.POSITIVE_INFINITY)));
        assertEquals(false, ExactConversionsSupport.isDoubleToIntExact((Double.NEGATIVE_INFINITY)));
        assertEquals(false, ExactConversionsSupport.isDoubleToIntExact((-0.0d)));
        assertEquals(true,  ExactConversionsSupport.isDoubleToIntExact((+0.0d)));
    }
    public static void testLong() {
        assertEquals(false, ExactConversionsSupport.isFloatToLongExact((Float.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isFloatToLongExact(((float) 0)));
        assertEquals(false, ExactConversionsSupport.isFloatToLongExact((Float.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isFloatToLongExact((Float.NaN)));
        assertEquals(false, ExactConversionsSupport.isFloatToLongExact((Float.POSITIVE_INFINITY)));
        assertEquals(false, ExactConversionsSupport.isFloatToLongExact((Float.NEGATIVE_INFINITY)));
        assertEquals(false, ExactConversionsSupport.isFloatToLongExact((-0.0f)));
        assertEquals(true,  ExactConversionsSupport.isFloatToLongExact((+0.0f)));
        assertEquals(false, ExactConversionsSupport.isDoubleToLongExact((Double.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isDoubleToLongExact(((double) 0)));
        assertEquals(false, ExactConversionsSupport.isDoubleToLongExact((Double.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isDoubleToLongExact((Double.NaN)));
        assertEquals(false, ExactConversionsSupport.isDoubleToLongExact((Double.POSITIVE_INFINITY)));
        assertEquals(false, ExactConversionsSupport.isDoubleToLongExact((Double.NEGATIVE_INFINITY)));
        assertEquals(false, ExactConversionsSupport.isDoubleToLongExact((-0.0d)));
        assertEquals(true,  ExactConversionsSupport.isDoubleToLongExact((+0.0d)));
    }
    public static void testFloat() {
        assertEquals(true,  ExactConversionsSupport.isIntToFloatExact(((byte) (Byte.MAX_VALUE))));
        assertEquals(true,  ExactConversionsSupport.isIntToFloatExact(((byte) (0))));
        assertEquals(true,  ExactConversionsSupport.isIntToFloatExact(((byte) (Byte.MIN_VALUE))));
        assertEquals(true,  ExactConversionsSupport.isIntToFloatExact(((short) (Short.MAX_VALUE))));
        assertEquals(true,  ExactConversionsSupport.isIntToFloatExact(((short) (0))));
        assertEquals(true,  ExactConversionsSupport.isIntToFloatExact(((short) (Short.MIN_VALUE))));
        assertEquals(true,  ExactConversionsSupport.isIntToFloatExact(((char) (Character.MAX_VALUE))));
        assertEquals(true,  ExactConversionsSupport.isIntToFloatExact(((char) (Character.MIN_VALUE))));
        assertEquals(false, ExactConversionsSupport.isIntToFloatExact( (Integer.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isIntToFloatExact((0)));
        assertEquals(true,  ExactConversionsSupport.isIntToFloatExact((Integer.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isLongToFloatExact((Long.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isLongToFloatExact((0l)));
        assertEquals(true,  ExactConversionsSupport.isLongToFloatExact((Long.MIN_VALUE)));
        assertEquals(false, ExactConversionsSupport.isDoubleToFloatExact((Double.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isDoubleToFloatExact(((double) 0)));
        assertEquals(false, ExactConversionsSupport.isDoubleToFloatExact((Double.MIN_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isDoubleToFloatExact((Double.NaN)));
        assertEquals(true,  ExactConversionsSupport.isDoubleToFloatExact((Double.POSITIVE_INFINITY)));
        assertEquals(true,  ExactConversionsSupport.isDoubleToFloatExact((Double.NEGATIVE_INFINITY)));
        assertEquals(true,  ExactConversionsSupport.isDoubleToFloatExact((-0.0d)));
        assertEquals(true,  ExactConversionsSupport.isDoubleToFloatExact((+0.0d)));
    }
    public static void testDouble() {
        assertEquals(false, ExactConversionsSupport.isLongToDoubleExact((Long.MAX_VALUE)));
        assertEquals(true,  ExactConversionsSupport.isLongToDoubleExact((0L)));
        assertEquals(true,  ExactConversionsSupport.isLongToDoubleExact((Long.MIN_VALUE)));
    }

    static void assertEquals(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }
}
