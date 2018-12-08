/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

/**
 * Tests the VM independent intrinsification of {@link Unsafe} methods.
 */
public class UnsafeSubstitutionsTest extends MethodSubstitutionTest {

    public void testSubstitution(String testMethodName, Class<?> holder, String methodName, Class<?>[] parameterTypes, Object receiver, Object[] args1, Object[] args2) {
        ResolvedJavaMethod testMethod = getResolvedJavaMethod(testMethodName);
        ResolvedJavaMethod originalMethod = getResolvedJavaMethod(holder, methodName, parameterTypes);

        // Force compilation
        InstalledCode code = getCode(testMethod);
        assert code != null;

        // Verify that the original method and the substitution produce the same value
        Object expected = invokeSafe(originalMethod, receiver, args1);
        Object actual = invokeSafe(testMethod, null, args2);
        assertDeepEquals(expected, actual);

        // Verify that the generated code and the original produce the same value
        expected = invokeSafe(originalMethod, receiver, args1);
        actual = executeVarargsSafe(code, args2);
        assertDeepEquals(expected, actual);

    }

    static long off(Object o, String name) {
        try {
            return UNSAFE.objectFieldOffset(o.getClass().getDeclaredField(name));
        } catch (Exception e) {
            Assert.fail(e.toString());
            return 0L;
        }
    }

    static class Foo {
        boolean z;
        byte b;
        short s;
        char c;
        int i;
        long l;
        float f;
        double d;
        Object o;
    }

    @Test
    public void testUnsafeSubstitutions() throws Exception {
        test("unsafeCompareAndSwapInt", UNSAFE, supply(() -> new Foo()), fooOffset("i"));

        testGraph("unsafeCompareAndSwapInt");
        testGraph("unsafeCompareAndSwapLong");
        testGraph("unsafeCompareAndSwapObject");

        testGraph("unsafeGetBoolean");
        testGraph("unsafeGetByte");
        testGraph("unsafeGetShort");
        testGraph("unsafeGetChar");
        testGraph("unsafeGetInt");
        testGraph("unsafeGetLong");
        testGraph("unsafeGetFloat");
        testGraph("unsafeGetDouble");
        testGraph("unsafeGetObject");

        testGraph("unsafePutBoolean");
        testGraph("unsafePutByte");
        testGraph("unsafePutShort");
        testGraph("unsafePutChar");
        testGraph("unsafePutInt");
        testGraph("unsafePutLong");
        testGraph("unsafePutFloat");
        testGraph("unsafePutDouble");
        testGraph("unsafePutObject");

        testGraph("unsafeGetAddress");
        testGraph("unsafePutAddress");

        testGraph("unsafeDirectMemoryRead");
        testGraph("unsafeDirectMemoryWrite");

        long address = UNSAFE.allocateMemory(8 * JavaKind.values().length);
        for (Unsafe unsafeArg : new Unsafe[]{UNSAFE, null}) {
            test("unsafeCompareAndSwapInt", unsafeArg, supply(() -> new Foo()), fooOffset("i"));
            test("unsafeCompareAndSwapLong", unsafeArg, supply(() -> new Foo()), fooOffset("l"));
            test("unsafeCompareAndSwapObject", unsafeArg, supply(() -> new Foo()), fooOffset("o"));

            test("unsafeGetBoolean", unsafeArg, supply(() -> new Foo()), fooOffset("z"));
            test("unsafeGetByte", unsafeArg, supply(() -> new Foo()), fooOffset("b"));
            test("unsafeGetShort", unsafeArg, supply(() -> new Foo()), fooOffset("s"));
            test("unsafeGetChar", unsafeArg, supply(() -> new Foo()), fooOffset("c"));
            test("unsafeGetInt", unsafeArg, supply(() -> new Foo()), fooOffset("i"));
            test("unsafeGetLong", unsafeArg, supply(() -> new Foo()), fooOffset("l"));
            test("unsafeGetFloat", unsafeArg, supply(() -> new Foo()), fooOffset("f"));
            test("unsafeGetDouble", unsafeArg, supply(() -> new Foo()), fooOffset("d"));
            test("unsafeGetObject", unsafeArg, supply(() -> new Foo()), fooOffset("o"));

            test("unsafePutBoolean", unsafeArg, supply(() -> new Foo()), fooOffset("z"), true);
            test("unsafePutByte", unsafeArg, supply(() -> new Foo()), fooOffset("b"), (byte) 87);
            test("unsafePutShort", unsafeArg, supply(() -> new Foo()), fooOffset("s"), (short) -93);
            test("unsafePutChar", unsafeArg, supply(() -> new Foo()), fooOffset("c"), 'A');
            test("unsafePutInt", unsafeArg, supply(() -> new Foo()), fooOffset("i"), 42);
            test("unsafePutLong", unsafeArg, supply(() -> new Foo()), fooOffset("l"), 4711L);
            test("unsafePutFloat", unsafeArg, supply(() -> new Foo()), fooOffset("f"), 58.0F);
            test("unsafePutDouble", unsafeArg, supply(() -> new Foo()), fooOffset("d"), -28736.243465D);
            test("unsafePutObject", unsafeArg, supply(() -> new Foo()), fooOffset("i"), "value1", "value2", "value3");

            test("unsafeGetAddress", unsafeArg, address);
            test("unsafePutAddress", unsafeArg, address, 0xDEAD_BEEF_DEAD_BABEL);

            test("unsafeDirectMemoryRead", unsafeArg, address);
            test("unsafeDirectMemoryWrite", unsafeArg, address, 0xCAFE_BABE_DEAD_BABEL);
        }
        UNSAFE.freeMemory(address);
    }

    private static long fooOffset(String name) {
        try {
            return UNSAFE.objectFieldOffset(Foo.class.getDeclaredField(name));
        } catch (NoSuchFieldException | SecurityException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapInt(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapInt(obj, offset, 0, 1);
    }

    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapLong(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapLong(obj, offset, 0, 1);
    }

    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapObject(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapObject(obj, offset, null, new Object());
    }

    @SuppressWarnings("all")
    public static boolean unsafeGetBoolean(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getBoolean(obj, offset) && unsafe.getBooleanVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetByte(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getByte(obj, offset) + unsafe.getByteVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetShort(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getShort(obj, offset) + unsafe.getShortVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetChar(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getChar(obj, offset) + unsafe.getCharVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetInt(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getInt(obj, offset) + unsafe.getIntVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static long unsafeGetLong(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getLong(obj, offset) + unsafe.getLongVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static float unsafeGetFloat(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getFloat(obj, offset) + unsafe.getFloatVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static double unsafeGetDouble(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getDouble(obj, offset) + unsafe.getDoubleVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static boolean unsafeGetObject(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getObject(obj, offset) == unsafe.getObjectVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafePutBoolean(Unsafe unsafe, Object obj, long offset, boolean value) {
        int res = 1;
        unsafe.putBoolean(obj, offset, value);
        res += unsafe.getBoolean(obj, offset) ? 3 : 5;
        unsafe.putBooleanVolatile(obj, offset, value);
        res += unsafe.getBoolean(obj, offset) ? 7 : 11;
        return res;
    }

    @SuppressWarnings("all")
    public static int unsafePutByte(Unsafe unsafe, Object obj, long offset, byte value) {
        int res = 1;
        unsafe.putByte(obj, offset, (byte) (value + 1));
        res += unsafe.getByte(obj, offset);
        unsafe.putByteVolatile(obj, offset, (byte) (value + 2));
        res += unsafe.getByte(obj, offset);
        return res;
    }

    @SuppressWarnings("all")
    public static int unsafePutShort(Unsafe unsafe, Object obj, long offset, short value) {
        int res = 1;
        unsafe.putShort(obj, offset, (short) (value + 1));
        res += unsafe.getShort(obj, offset);
        unsafe.putShortVolatile(obj, offset, (short) (value + 2));
        res += unsafe.getShort(obj, offset);
        return res;
    }

    @SuppressWarnings("all")
    public static int unsafePutChar(Unsafe unsafe, Object obj, long offset, char value) {
        int res = 1;
        unsafe.putChar(obj, offset, (char) (value + 1));
        res += unsafe.getChar(obj, offset);
        unsafe.putCharVolatile(obj, offset, (char) (value + 2));
        res += unsafe.getChar(obj, offset);
        return res;
    }

    @SuppressWarnings("all")
    public static int unsafePutInt(Unsafe unsafe, Object obj, long offset, int value) {
        int res = 1;
        unsafe.putInt(obj, offset, value);
        res += unsafe.getInt(obj, offset);
        unsafe.putIntVolatile(obj, offset, value + 1);
        res += unsafe.getInt(obj, offset);
        unsafe.putOrderedInt(obj, offset, value + 2);
        res += unsafe.getInt(obj, offset);
        return res;
    }

    @SuppressWarnings("all")
    public static long unsafePutLong(Unsafe unsafe, Object obj, long offset, long value) {
        long res = 1;
        unsafe.putLong(obj, offset, value + 1);
        res += unsafe.getLong(obj, offset);
        unsafe.putLongVolatile(obj, offset, value + 2);
        res += unsafe.getLong(obj, offset);
        unsafe.putOrderedLong(obj, offset, value + 3);
        res += unsafe.getLong(obj, offset);
        return res;
    }

    @SuppressWarnings("all")
    public static float unsafePutFloat(Unsafe unsafe, Object obj, long offset, float value) {
        float res = 1;
        unsafe.putFloat(obj, offset, value + 1.0F);
        res += unsafe.getFloat(obj, offset);
        unsafe.putFloatVolatile(obj, offset, value + 2.0F);
        res += unsafe.getFloat(obj, offset);
        return res;
    }

    @SuppressWarnings("all")
    public static double unsafePutDouble(Unsafe unsafe, Object obj, long offset, double value) {
        double res = 1;
        unsafe.putDouble(obj, offset, value);
        res += unsafe.getDouble(obj, offset);
        unsafe.putDoubleVolatile(obj, offset, value);
        res += unsafe.getDouble(obj, offset);
        return res;
    }

    @SuppressWarnings("all")
    public static Object[] unsafePutObject(Unsafe unsafe, Object obj, long offset, Object value1, Object value2, Object value3) {
        Object[] res = new Object[3];
        unsafe.putObject(obj, offset, value1);
        res[0] = unsafe.getObject(obj, offset);
        unsafe.putObjectVolatile(obj, offset, value2);
        res[1] = unsafe.getObject(obj, offset);
        unsafe.putOrderedObject(obj, offset, value3);
        res[2] = unsafe.getObject(obj, offset);
        return res;
    }

    @SuppressWarnings("all")
    public static long unsafeGetAddress(Unsafe unsafe, long offset) {
        return unsafe.getAddress(offset);
    }

    @SuppressWarnings("all")
    public static long unsafePutAddress(Unsafe unsafe, long offset, long value) {
        long res = 1;
        unsafe.putAddress(offset, value);
        res += unsafe.getAddress(offset);
        return res;
    }

    @SuppressWarnings("all")
    public static double unsafeDirectMemoryRead(Unsafe unsafe, long address) {
        // Unsafe.getBoolean(long) and Unsafe.getObject(long) do not exist
        // @formatter:off
        return unsafe.getByte(address) +
               unsafe.getShort(address + 8) +
               unsafe.getChar(address + 16) +
               unsafe.getInt(address + 24) +
               unsafe.getLong(address + 32) +
               unsafe.getFloat(address + 40) +
               unsafe.getDouble(address + 48);
        // @formatter:on
    }

    @SuppressWarnings("all")
    public static double unsafeDirectMemoryWrite(Unsafe unsafe, long address, long value) {
        // Unsafe.putBoolean(long) and Unsafe.putObject(long) do not exist
        unsafe.putByte(address + 0, (byte) value);
        unsafe.putShort(address + 8, (short) value);
        unsafe.putChar(address + 16, (char) value);
        unsafe.putInt(address + 24, (int) value);
        unsafe.putLong(address + 32, value);
        unsafe.putFloat(address + 40, value);
        unsafe.putDouble(address + 48, value);
        return unsafeDirectMemoryRead(unsafe, address);
    }

    static class MyObject {
        int i = 42;
        final int j = 24;
        final String a = "a";
        final String b;

        MyObject(String b) {
            this.b = b;
            Thread.dumpStack();
        }

        @Override
        public String toString() {
            return j + a + b + i;
        }
    }

    @SuppressWarnings("all")
    public static String unsafeAllocateInstance(Unsafe unsafe) throws InstantiationException {
        return unsafe.allocateInstance(MyObject.class).toString();
    }

    @Test
    public void testAllocateInstance() throws Exception {
        unsafeAllocateInstance(UNSAFE);
        test("unsafeAllocateInstance", UNSAFE);
        test("unsafeAllocateInstance", (Object) null);
    }

    @Test
    public void testGetAndAddInt() throws Exception {
        Foo f1 = new Foo();
        Foo f2 = new Foo();
        long offset = off(f1, "i");
        Class<?>[] parameterTypes = new Class<?>[]{Object.class, long.class, int.class};
        for (int delta = Integer.MAX_VALUE - 10; delta < Integer.MAX_VALUE; delta++) {
            Object[] args1 = new Object[]{f1, offset, delta};
            Object[] args2 = new Object[]{f2, offset, delta};
            testSubstitution("getAndAddInt", Unsafe.class, "getAndAddInt", parameterTypes, UNSAFE, args1, args2);
        }
    }

    public static int getAndAddInt(Object obj, long offset, int delta) {
        return UNSAFE.getAndAddInt(obj, offset, delta);
    }

    @Test
    public void testGetAndAddLong() throws Exception {
        Foo f1 = new Foo();
        Foo f2 = new Foo();
        long offset = off(f1, "l");
        Class<?>[] parameterTypes = new Class<?>[]{Object.class, long.class, long.class};
        for (long delta = Long.MAX_VALUE - 10; delta < Long.MAX_VALUE; delta++) {
            Object[] args1 = new Object[]{f1, offset, delta};
            Object[] args2 = new Object[]{f2, offset, delta};
            testSubstitution("getAndAddLong", Unsafe.class, "getAndAddLong", parameterTypes, UNSAFE, args1, args2);
        }
    }

    public static long getAndAddLong(Object obj, long offset, long delta) {
        return UNSAFE.getAndAddLong(obj, offset, delta);
    }

    @Test
    public void testGetAndSetInt() throws Exception {
        Foo f1 = new Foo();
        Foo f2 = new Foo();
        long offset = off(f1, "i");
        Class<?>[] parameterTypes = new Class<?>[]{Object.class, long.class, int.class};
        for (int delta = Integer.MAX_VALUE - 10; delta < Integer.MAX_VALUE; delta++) {
            Object[] args1 = new Object[]{f1, offset, delta};
            Object[] args2 = new Object[]{f2, offset, delta};
            testSubstitution("getAndSetInt", Unsafe.class, "getAndSetInt", parameterTypes, UNSAFE, args1, args2);
        }
    }

    public static int getAndSetInt(Object obj, long offset, int newValue) {
        return UNSAFE.getAndSetInt(obj, offset, newValue);
    }

    @Test
    public void testGetAndSetLong() throws Exception {
        Foo f1 = new Foo();
        Foo f2 = new Foo();
        long offset = off(f1, "l");
        Class<?>[] parameterTypes = new Class<?>[]{Object.class, long.class, long.class};
        for (long newValue = Long.MAX_VALUE - 10; newValue < Long.MAX_VALUE; newValue++) {
            Object[] args1 = new Object[]{f1, offset, newValue};
            Object[] args2 = new Object[]{f2, offset, newValue};
            testSubstitution("getAndSetLong", Unsafe.class, "getAndSetLong", parameterTypes, UNSAFE, args1, args2);
        }
    }

    public static long getAndSetLong(Object obj, long offset, long newValue) {
        return UNSAFE.getAndSetLong(obj, offset, newValue);
    }

    @Test
    public void testGetAndSetObject() throws Exception {
        Foo f1 = new Foo();
        Foo f2 = new Foo();
        long offset = off(f1, "o");
        Class<?>[] parameterTypes = new Class<?>[]{Object.class, long.class, Object.class};
        for (long i = 0; i < 10; i++) {
            Object o = new Object();
            Object[] args1 = new Object[]{f1, offset, o};
            Object[] args2 = new Object[]{f2, offset, o};
            testSubstitution("getAndSetObject", Unsafe.class, "getAndSetObject", parameterTypes, UNSAFE, args1, args2);
            System.gc();
        }
    }

    public static Object getAndSetObject(Object obj, long offset, Object newValue) {
        return UNSAFE.getAndSetObject(obj, offset, newValue);
    }

}
