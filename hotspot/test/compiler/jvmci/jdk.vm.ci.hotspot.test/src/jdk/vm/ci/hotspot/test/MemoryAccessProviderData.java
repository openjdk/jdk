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

package jdk.vm.ci.hotspot.test;

import java.lang.reflect.Field;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.runtime.JVMCI;
import org.testng.annotations.DataProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Constant;
import jdk.internal.misc.Unsafe;

public class MemoryAccessProviderData {
    private static final Unsafe UNSAFE = getUnsafe();
    private static final HotSpotConstantReflectionProvider CONSTANT_REFLECTION = (HotSpotConstantReflectionProvider) JVMCI.getRuntime().getHostJVMCIBackend().getConstantReflection();
    private static final TestClass TEST_OBJECT = new TestClass();
    private static final JavaConstant TEST_CONSTANT = CONSTANT_REFLECTION.forObject(TEST_OBJECT);
    private static final JavaConstant TEST_CLASS_CONSTANT = CONSTANT_REFLECTION.forObject(TestClass.class);

    private static Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Unable to get Unsafe instance.", e);
        }
    }

    @DataProvider(name = "positiveObject")
    public static Object[][] getPositiveObjectJavaKind() {
        HotSpotJVMCIRuntimeProvider runtime = (HotSpotJVMCIRuntimeProvider) JVMCI.getRuntime();
        int offset = runtime.getConfig().classMirrorOffset;
        Constant wrappedKlassPointer = ((HotSpotResolvedObjectType) runtime.fromClass(TestClass.class)).klass();
        return new Object[][]{new Object[]{JavaKind.Object, wrappedKlassPointer, (long) offset, TEST_CLASS_CONSTANT, 0}};
    }

    @DataProvider(name = "positivePrimitive")
    public static Object[][] getPositivePrimitiveJavaKinds() {
        Field booleanField;
        Field byteField;
        Field shortField;
        Field intField;
        Field longField;
        Field floatField;
        Field doubleField;
        Field charField;
        try {
            booleanField = MemoryAccessProviderData.TestClass.class.getDeclaredField("booleanField");
            byteField = MemoryAccessProviderData.TestClass.class.getDeclaredField("byteField");
            shortField = MemoryAccessProviderData.TestClass.class.getDeclaredField("shortField");
            intField = MemoryAccessProviderData.TestClass.class.getDeclaredField("intField");
            longField = MemoryAccessProviderData.TestClass.class.getDeclaredField("longField");
            floatField = MemoryAccessProviderData.TestClass.class.getDeclaredField("floatField");
            doubleField = MemoryAccessProviderData.TestClass.class.getDeclaredField("doubleField");
            charField = MemoryAccessProviderData.TestClass.class.getDeclaredField("charField");
        } catch (NoSuchFieldException e) {
            throw new Error("TESTBUG: can't find test field " + e, e);
        }
        long booleanFieldOffset = UNSAFE.objectFieldOffset(booleanField);
        long byteFieldOffset = UNSAFE.objectFieldOffset(byteField);
        long shortFieldOffset = UNSAFE.objectFieldOffset(shortField);
        long intFieldOffset = UNSAFE.objectFieldOffset(intField);
        long longFieldOffset = UNSAFE.objectFieldOffset(longField);
        long floatFieldOffset = UNSAFE.objectFieldOffset(floatField);
        long doubleFieldOffset = UNSAFE.objectFieldOffset(doubleField);
        long charFieldOffset = UNSAFE.objectFieldOffset(charField);
        return new Object[][]{
                        new Object[]{JavaKind.Boolean, TEST_CONSTANT, booleanFieldOffset,
                                        JavaConstant.forBoolean(TEST_OBJECT.booleanField), 8},
                        new Object[]{JavaKind.Byte, TEST_CONSTANT, byteFieldOffset,
                                        JavaConstant.forByte(TEST_OBJECT.byteField), 8},
                        new Object[]{JavaKind.Short, TEST_CONSTANT, shortFieldOffset,
                                        JavaConstant.forShort(TEST_OBJECT.shortField), 16},
                        new Object[]{JavaKind.Int, TEST_CONSTANT, intFieldOffset,
                                        JavaConstant.forInt(TEST_OBJECT.intField), 32},
                        new Object[]{JavaKind.Long, TEST_CONSTANT, longFieldOffset,
                                        JavaConstant.forLong(TEST_OBJECT.longField), 64},
                        new Object[]{JavaKind.Float, TEST_CONSTANT, floatFieldOffset,
                                        JavaConstant.forFloat(TEST_OBJECT.floatField), 32},
                        new Object[]{JavaKind.Double, TEST_CONSTANT, doubleFieldOffset,
                                        JavaConstant.forDouble(TEST_OBJECT.doubleField), 64},
                        new Object[]{JavaKind.Char, TEST_CONSTANT, charFieldOffset,
                                        JavaConstant.forChar(TEST_OBJECT.charField), 16}};
    }

    @DataProvider(name = "negative")
    public static Object[][] getNegativeJavaKinds() {
        return new Object[][]{
                        new Object[]{JavaKind.Void, JavaConstant.NULL_POINTER},
                        new Object[]{JavaKind.Illegal, JavaConstant.INT_1}};
    }

    private static class TestClass {
        public final boolean booleanField = true;
        public final byte byteField = 2;
        public final short shortField = 3;
        public final int intField = 4;
        public final long longField = 5L;
        public final double doubleField = 6.0d;
        public final float floatField = 7.0f;
        public final char charField = 'a';
        public final String stringField = "abc";
    }
}
