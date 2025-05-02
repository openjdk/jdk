/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @requires sun.arch.data.model == "64"
 * @compile platform/PlatformLayouts.java
 * @modules java.base/jdk.internal.foreign
 *          java.base/jdk.internal.foreign.abi
 *          java.base/jdk.internal.foreign.abi.aarch64
 * @build CallArrangerTestBase
 * @run testng TestMacOsAArch64CallArranger
 */

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.MemorySegment;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.StubLocations;
import jdk.internal.foreign.abi.VMStorage;
import jdk.internal.foreign.abi.aarch64.CallArranger;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.MethodType;

import static java.lang.foreign.Linker.Option.firstVariadicArg;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static jdk.internal.foreign.abi.Binding.*;
import static jdk.internal.foreign.abi.aarch64.AArch64Architecture.*;
import static jdk.internal.foreign.abi.aarch64.AArch64Architecture.Regs.*;
import static platform.PlatformLayouts.AArch64.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestMacOsAArch64CallArranger extends CallArrangerTestBase {

    private static final VMStorage TARGET_ADDRESS_STORAGE = StubLocations.TARGET_ADDRESS.storage(StorageType.PLACEHOLDER);

    @Test
    public void testVarArgsOnStack() {
        MethodType mt = MethodType.methodType(void.class, int.class, int.class, float.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_INT, C_INT, C_FLOAT);
        FunctionDescriptor fdExpected = FunctionDescriptor.ofVoid(ADDRESS, C_INT, C_INT, C_FLOAT);
        CallArranger.Bindings bindings = CallArranger.MACOS.getBindings(mt, fd, false, LinkerOptions.forDowncall(fd, firstVariadicArg(1)));

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fdExpected);

        // The two variadic arguments should be allocated on the stack
        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, int.class) },
            { vmStore(stackStorage((short) 4, 0), int.class) },
            { vmStore(stackStorage((short) 4, 8), float.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testMacArgsOnStack() {
        MethodType mt = MethodType.methodType(void.class,
                int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class,
                int.class, int.class, short.class, byte.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_INT, C_INT, C_INT, C_INT,
                C_INT, C_INT, C_INT, C_INT,
                C_INT, C_INT, C_SHORT, C_CHAR);
        CallArranger.Bindings bindings = CallArranger.MACOS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, int.class) },
            { vmStore(r1, int.class) },
            { vmStore(r2, int.class) },
            { vmStore(r3, int.class) },
            { vmStore(r4, int.class) },
            { vmStore(r5, int.class) },
            { vmStore(r6, int.class) },
            { vmStore(r7, int.class) },
            { vmStore(stackStorage((short) 4, 0), int.class) },
            { vmStore(stackStorage((short) 4, 4), int.class) },
            { cast(short.class, int.class), vmStore(stackStorage((short) 2, 8), int.class) },
            { cast(byte.class, int.class), vmStore(stackStorage((short) 1, 10), int.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testMacArgsOnStack2() {
        StructLayout struct = MemoryLayout.structLayout(
            C_FLOAT,
            C_FLOAT
        );
        MethodType mt = MethodType.methodType(void.class,
                long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class,
                double.class, double.class, double.class, double.class,
                double.class, double.class, double.class, double.class,
                int.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE,
                C_INT, struct);
        CallArranger.Bindings bindings = CallArranger.MACOS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, long.class) },
            { vmStore(r1, long.class) },
            { vmStore(r2, long.class) },
            { vmStore(r3, long.class) },
            { vmStore(r4, long.class) },
            { vmStore(r5, long.class) },
            { vmStore(r6, long.class) },
            { vmStore(r7, long.class) },
            { vmStore(v0, double.class) },
            { vmStore(v1, double.class) },
            { vmStore(v2, double.class) },
            { vmStore(v3, double.class) },
            { vmStore(v4, double.class) },
            { vmStore(v5, double.class) },
            { vmStore(v6, double.class) },
            { vmStore(v7, double.class) },
            { vmStore(stackStorage((short) 4, 0), int.class) },
            {
                dup(),
                bufferLoad(0, int.class),
                vmStore(stackStorage((short) 4, 4), int.class),
                bufferLoad(4, int.class),
                vmStore(stackStorage((short) 4, 8), int.class),
            }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testMacArgsOnStack3() {
        StructLayout struct = MemoryLayout.structLayout(
            C_POINTER,
            C_POINTER
        );
        MethodType mt = MethodType.methodType(void.class,
                long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class,
                double.class, double.class, double.class, double.class,
                double.class, double.class, double.class, double.class,
                MemorySegment.class, float.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE,
                struct, C_FLOAT);
        CallArranger.Bindings bindings = CallArranger.MACOS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, long.class) },
            { vmStore(r1, long.class) },
            { vmStore(r2, long.class) },
            { vmStore(r3, long.class) },
            { vmStore(r4, long.class) },
            { vmStore(r5, long.class) },
            { vmStore(r6, long.class) },
            { vmStore(r7, long.class) },
            { vmStore(v0, double.class) },
            { vmStore(v1, double.class) },
            { vmStore(v2, double.class) },
            { vmStore(v3, double.class) },
            { vmStore(v4, double.class) },
            { vmStore(v5, double.class) },
            { vmStore(v6, double.class) },
            { vmStore(v7, double.class) },
            { dup(),
                bufferLoad(0, long.class), vmStore(stackStorage((short) 8, 0), long.class),
                bufferLoad(8, long.class), vmStore(stackStorage((short) 8, 8), long.class) },
            { vmStore(stackStorage((short) 4, 16), float.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testMacArgsOnStack4() {
        StructLayout struct = MemoryLayout.structLayout(
            C_INT,
            C_INT,
            C_POINTER
        );
        MethodType mt = MethodType.methodType(void.class,
                long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class,
                double.class, double.class, double.class, double.class,
                double.class, double.class, double.class, double.class,
                float.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE,
                C_FLOAT, struct);
        CallArranger.Bindings bindings = CallArranger.MACOS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, long.class) },
            { vmStore(r1, long.class) },
            { vmStore(r2, long.class) },
            { vmStore(r3, long.class) },
            { vmStore(r4, long.class) },
            { vmStore(r5, long.class) },
            { vmStore(r6, long.class) },
            { vmStore(r7, long.class) },
            { vmStore(v0, double.class) },
            { vmStore(v1, double.class) },
            { vmStore(v2, double.class) },
            { vmStore(v3, double.class) },
            { vmStore(v4, double.class) },
            { vmStore(v5, double.class) },
            { vmStore(v6, double.class) },
            { vmStore(v7, double.class) },
            { vmStore(stackStorage((short) 4, 0), float.class) },
            { dup(),
                bufferLoad(0, long.class), vmStore(stackStorage((short) 8, 8), long.class),
                bufferLoad(8, long.class), vmStore(stackStorage((short) 8, 16), long.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    // structs that are passed field-wise should not have padding after them
    @Test
    public void testMacArgsOnStack5() {
        StructLayout struct = MemoryLayout.structLayout(
            C_FLOAT
        );
        MethodType mt = MethodType.methodType(void.class,
                long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class,
                double.class, double.class, double.class, double.class,
                double.class, double.class, double.class, double.class,
                MemorySegment.class, int.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE,
                struct, C_INT, C_POINTER);
        CallArranger.Bindings bindings = CallArranger.MACOS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, long.class) },
            { vmStore(r1, long.class) },
            { vmStore(r2, long.class) },
            { vmStore(r3, long.class) },
            { vmStore(r4, long.class) },
            { vmStore(r5, long.class) },
            { vmStore(r6, long.class) },
            { vmStore(r7, long.class) },
            { vmStore(v0, double.class) },
            { vmStore(v1, double.class) },
            { vmStore(v2, double.class) },
            { vmStore(v3, double.class) },
            { vmStore(v4, double.class) },
            { vmStore(v5, double.class) },
            { vmStore(v6, double.class) },
            { vmStore(v7, double.class) },
            {
                bufferLoad(0, int.class),
                vmStore(stackStorage((short) 4, 0), int.class),
            },
            { vmStore(stackStorage((short) 4, 4), int.class) },
            { unboxAddress(), vmStore(stackStorage((short) 8, 8), long.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    // structs that are passed chunk-wise should have padding before them, as well as after
    @Test
    public void testMacArgsOnStack6() {
        StructLayout struct = MemoryLayout.structLayout(
            C_INT
        );
        MethodType mt = MethodType.methodType(void.class,
                long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class,
                double.class, double.class, double.class, double.class,
                double.class, double.class, double.class, double.class,
                int.class, MemorySegment.class, double.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE,
                C_INT, struct, C_DOUBLE, C_POINTER);
        CallArranger.Bindings bindings = CallArranger.MACOS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, long.class) },
            { vmStore(r1, long.class) },
            { vmStore(r2, long.class) },
            { vmStore(r3, long.class) },
            { vmStore(r4, long.class) },
            { vmStore(r5, long.class) },
            { vmStore(r6, long.class) },
            { vmStore(r7, long.class) },
            { vmStore(v0, double.class) },
            { vmStore(v1, double.class) },
            { vmStore(v2, double.class) },
            { vmStore(v3, double.class) },
            { vmStore(v4, double.class) },
            { vmStore(v5, double.class) },
            { vmStore(v6, double.class) },
            { vmStore(v7, double.class) },
            { vmStore(stackStorage((short) 4, 0), int.class) },
            {
                bufferLoad(0, int.class),
                vmStore(stackStorage((short) 4, 8), int.class),
            },
            { vmStore(stackStorage((short) 8, 16), double.class) },
            { unboxAddress(), vmStore(stackStorage((short) 8, 24), long.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }
}
