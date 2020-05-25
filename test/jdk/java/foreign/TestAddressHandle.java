/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestAddressHandle
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestAddressHandle
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestAddressHandle
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestAddressHandle
 */

import java.lang.invoke.*;
import java.nio.ByteOrder;
import jdk.incubator.foreign.*;

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TestAddressHandle {

    static final MethodHandle INT_TO_BOOL;
    static final MethodHandle BOOL_TO_INT;
    static final MethodHandle INT_TO_STRING;
    static final MethodHandle STRING_TO_INT;

    static {
        try {
            INT_TO_BOOL = MethodHandles.lookup().findStatic(TestAddressHandle.class, "intToBool",
                    MethodType.methodType(boolean.class, int.class));
            BOOL_TO_INT = MethodHandles.lookup().findStatic(TestAddressHandle.class, "boolToInt",
                    MethodType.methodType(int.class, boolean.class));
            INT_TO_STRING = MethodHandles.lookup().findStatic(TestAddressHandle.class, "intToString",
                    MethodType.methodType(String.class, int.class));
            STRING_TO_INT = MethodHandles.lookup().findStatic(TestAddressHandle.class, "stringToInt",
                    MethodType.methodType(int.class, String.class));
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Test(dataProvider = "addressHandles")
    public void testAddressHandle(VarHandle addrHandle) {
        VarHandle longHandle = MemoryHandles.varHandle(long.class, ByteOrder.nativeOrder());
        try (MemorySegment segment = MemorySegment.allocateNative(8)) {
            longHandle.set(segment.baseAddress(), 42L);
            MemoryAddress address = (MemoryAddress)addrHandle.get(segment.baseAddress());
            assertEquals(address.toRawLongValue(), 42L);
            try {
                longHandle.get(address); // check that address cannot be de-referenced
                fail();
            } catch (UnsupportedOperationException ex) {
                assertTrue(true);
            }
            addrHandle.set(segment.baseAddress(), address.addOffset(1));
            long result = (long)longHandle.get(segment.baseAddress());
            assertEquals(43L, result);
        }
    }

    @Test(dataProvider = "addressHandles")
    public void testNull(VarHandle addrHandle) {
        VarHandle longHandle = MemoryHandles.varHandle(long.class, ByteOrder.nativeOrder());
        try (MemorySegment segment = MemorySegment.allocateNative(8)) {
            longHandle.set(segment.baseAddress(), 0L);
            MemoryAddress address = (MemoryAddress)addrHandle.get(segment.baseAddress());
            assertTrue(address == MemoryAddress.NULL);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadAdaptFloat() {
        VarHandle floatHandle = MemoryHandles.varHandle(float.class, ByteOrder.nativeOrder());
        MemoryHandles.asAddressVarHandle(floatHandle);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadAdaptDouble() {
        VarHandle doubleHandle = MemoryHandles.varHandle(double.class, ByteOrder.nativeOrder());
        MemoryHandles.asAddressVarHandle(doubleHandle);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadAdaptBoolean() {
        VarHandle intHandle = MemoryHandles.varHandle(int.class, ByteOrder.nativeOrder());
        VarHandle boolHandle = MemoryHandles.filterValue(intHandle, BOOL_TO_INT, INT_TO_BOOL);
        MemoryHandles.asAddressVarHandle(boolHandle);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadAdaptString() {
        VarHandle intHandle = MemoryHandles.varHandle(int.class, ByteOrder.nativeOrder());
        VarHandle stringHandle = MemoryHandles.filterValue(intHandle, STRING_TO_INT, INT_TO_STRING);
        MemoryHandles.asAddressVarHandle(stringHandle);
    }

    @DataProvider(name = "addressHandles")
    static Object[][] addressHandles() {
        return new Object[][] {
                // long
                { MemoryHandles.asAddressVarHandle(MemoryHandles.varHandle(long.class, ByteOrder.nativeOrder())) },
                { MemoryHandles.asAddressVarHandle(MemoryHandles.withOffset(MemoryHandles.varHandle(long.class, ByteOrder.nativeOrder()), 0)) },
                { MemoryHandles.asAddressVarHandle(MemoryLayouts.JAVA_LONG.varHandle(long.class)) },

                // int
                { MemoryHandles.asAddressVarHandle(MemoryHandles.varHandle(int.class, ByteOrder.nativeOrder())) },
                { MemoryHandles.asAddressVarHandle(MemoryHandles.withOffset(MemoryHandles.varHandle(int.class, ByteOrder.nativeOrder()), 0)) },
                { MemoryHandles.asAddressVarHandle(MemoryLayouts.JAVA_INT.varHandle(int.class)) },

                // short
                { MemoryHandles.asAddressVarHandle(MemoryHandles.varHandle(short.class, ByteOrder.nativeOrder())) },
                { MemoryHandles.asAddressVarHandle(MemoryHandles.withOffset(MemoryHandles.varHandle(short.class, ByteOrder.nativeOrder()), 0)) },
                { MemoryHandles.asAddressVarHandle(MemoryLayouts.JAVA_SHORT.varHandle(short.class)) },

                // char
                { MemoryHandles.asAddressVarHandle(MemoryHandles.varHandle(char.class, ByteOrder.nativeOrder())) },
                { MemoryHandles.asAddressVarHandle(MemoryHandles.withOffset(MemoryHandles.varHandle(char.class, ByteOrder.nativeOrder()), 0)) },
                { MemoryHandles.asAddressVarHandle(MemoryLayouts.JAVA_CHAR.varHandle(char.class)) },

                // byte
                { MemoryHandles.asAddressVarHandle(MemoryHandles.varHandle(byte.class, ByteOrder.nativeOrder())) },
                { MemoryHandles.asAddressVarHandle(MemoryHandles.withOffset(MemoryHandles.varHandle(byte.class, ByteOrder.nativeOrder()), 0)) },
                { MemoryHandles.asAddressVarHandle(MemoryLayouts.JAVA_BYTE.varHandle(byte.class)) }
        };
    }

    static int boolToInt(boolean value) {
        return value ? 1 : 0;
    }

    static boolean intToBool(int value) {
        return value != 0;
    }

    static int stringToInt(String value) {
        return value.length();
    }

    static String intToString(int value) {
        return String.valueOf(value);
    }
}
