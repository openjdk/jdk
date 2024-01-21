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
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestAccessModes
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestAccessModes
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestAccessModes
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestAccessModes
 */

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.invoke.VarHandle.AccessMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.testng.annotations.*;

import static org.testng.Assert.*;
public class TestAccessModes {

    @Test(dataProvider = "segmentsAndLayoutsAndModes")
    public void testAccessModes(MemorySegment segment, ValueLayout layout, AccessMode mode) throws Throwable {
        VarHandle varHandle = layout.varHandle();
        MethodHandle methodHandle = varHandle.toMethodHandle(mode);
        boolean compatible = AccessModeKind.supportedModes(layout).contains(AccessModeKind.of(mode));
        try {
            Object o = methodHandle.invokeWithArguments(makeArgs(segment, varHandle.accessModeType(mode)));
            assertTrue(compatible);
        } catch (UnsupportedOperationException ex) {
            assertFalse(compatible);
        } catch (IllegalArgumentException ex) {
            // access is unaligned, but access mode is supported
            assertTrue(compatible);
        }
    }

    Object[] makeArgs(MemorySegment segment, MethodType type) throws Throwable {
        List<Object> args = new ArrayList<>();
        args.add(segment);
        for (Class argType : type.dropParameterTypes(0, 1).parameterList()) {
            args.add(defaultValue(argType));
        }
        return args.toArray();
    }

    Object defaultValue(Class<?> clazz) throws Throwable {
        if (clazz == MemorySegment.class) {
            return MemorySegment.NULL;
        } else if (clazz.isPrimitive()) {
            return MethodHandles.zero(clazz).invoke();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /*
     * See the javadoc of MemoryLayout::varHandle.
     */
    enum AccessModeKind {
        PLAIN,
        READ_WRITE,
        ATOMIC_UPDATE,
        ATOMIC_NUMERIC_UPDATE,
        ATOMIC_BITWISE_UPDATE;

        static AccessModeKind of(AccessMode mode) {
            return switch (mode) {
                case GET, SET -> PLAIN;
                case GET_ACQUIRE, GET_OPAQUE, GET_VOLATILE, SET_VOLATILE,
                        SET_OPAQUE, SET_RELEASE -> READ_WRITE;
                case GET_AND_SET, GET_AND_SET_ACQUIRE, GET_AND_SET_RELEASE,
                        WEAK_COMPARE_AND_SET, WEAK_COMPARE_AND_SET_RELEASE,
                        WEAK_COMPARE_AND_SET_ACQUIRE, WEAK_COMPARE_AND_SET_PLAIN,
                        COMPARE_AND_EXCHANGE, COMPARE_AND_EXCHANGE_ACQUIRE,
                        COMPARE_AND_EXCHANGE_RELEASE, COMPARE_AND_SET -> ATOMIC_UPDATE;
                case GET_AND_ADD, GET_AND_ADD_ACQUIRE, GET_AND_ADD_RELEASE -> ATOMIC_NUMERIC_UPDATE;
                default -> ATOMIC_BITWISE_UPDATE;
            };
        }

        static Set<AccessModeKind> supportedModes(ValueLayout layout) {
            Set<AccessModeKind> supportedModes = EnumSet.noneOf(AccessModeKind.class);
            supportedModes.add(PLAIN);
            if (layout.byteAlignment() >= layout.byteSize()) {
                supportedModes.add(READ_WRITE);
                if (layout instanceof ValueLayout.OfInt || layout instanceof ValueLayout.OfLong ||
                        layout instanceof ValueLayout.OfFloat || layout instanceof ValueLayout.OfDouble ||
                        layout instanceof AddressLayout) {
                    supportedModes.add(ATOMIC_UPDATE);
                }
                if (layout instanceof ValueLayout.OfInt || layout instanceof ValueLayout.OfLong ||
                        layout instanceof AddressLayout) {
                    supportedModes.add(ATOMIC_NUMERIC_UPDATE);
                    supportedModes.add(ATOMIC_BITWISE_UPDATE);
                }
            }
            return supportedModes;
        }
    }

    static MemoryLayout[] layouts() {
        MemoryLayout[] valueLayouts = {
                ValueLayout.JAVA_BOOLEAN,
                ValueLayout.JAVA_CHAR,
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_SHORT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_FLOAT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_DOUBLE,
                ValueLayout.ADDRESS
        };
        List<MemoryLayout> layouts = new ArrayList<>();
        for (MemoryLayout layout : valueLayouts) {
            for (int align : new int[] { 1, 2, 4, 8 }) {
                layouts.add(layout.withByteAlignment(align));
            }
        }
        return layouts.toArray(new MemoryLayout[0]);
    }

    static MemorySegment[] segments() {
        return new MemorySegment[]{
                Arena.ofAuto().allocate(8),
                MemorySegment.ofArray(new byte[8]),
                MemorySegment.ofArray(new char[4]),
                MemorySegment.ofArray(new short[4]),
                MemorySegment.ofArray(new int[2]),
                MemorySegment.ofArray(new float[2]),
                MemorySegment.ofArray(new long[1]),
                MemorySegment.ofArray(new double[1])
        };
    }

    @DataProvider(name = "segmentsAndLayoutsAndModes")
    static Object[][] segmentsAndLayoutsAndModes() {
        List<Object[]> segmentsAndLayouts = new ArrayList<>();
        for (MemorySegment segment : segments()) {
            for (MemoryLayout layout : layouts()) {
                for (AccessMode mode : AccessMode.values()) {
                    segmentsAndLayouts.add(new Object[]{segment, layout, mode});
                }
            }
        }
        return segmentsAndLayouts.toArray(new Object[0][]);
    }

}
