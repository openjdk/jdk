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
 * @compile platform/PlatformLayouts.java
 * @modules java.base/jdk.internal.foreign
 *          java.base/jdk.internal.foreign.abi
 *          java.base/jdk.internal.foreign.abi.aarch64
 * @build CallArrangerTestBase
 * @run testng TestWindowsAArch64CallArranger
 */

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
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

public class TestWindowsAArch64CallArranger extends CallArrangerTestBase {

    private static final VMStorage TARGET_ADDRESS_STORAGE = StubLocations.TARGET_ADDRESS.storage(StorageType.PLACEHOLDER);
    private static final VMStorage RETURN_BUFFER_STORAGE = StubLocations.RETURN_BUFFER.storage(StorageType.PLACEHOLDER);

    @Test
    public void testWindowsArgsInRegs() {
        MethodType mt = MethodType.methodType(void.class, int.class, int.class, float.class, double.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_INT, C_INT, C_FLOAT, C_DOUBLE);
        CallArranger.Bindings bindings = CallArranger.WINDOWS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, int.class) },
            { vmStore(r1, int.class) },
            { vmStore(v0, float.class) },
            { vmStore(v1, double.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testWindowsVarArgsInRegs() {
        MethodType mt = MethodType.methodType(void.class, int.class, int.class, float.class, double.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_INT, C_INT, C_FLOAT, C_DOUBLE);
        FunctionDescriptor fdExpected = FunctionDescriptor.ofVoid(ADDRESS, C_INT, C_INT, C_FLOAT, C_DOUBLE);
        CallArranger.Bindings bindings = CallArranger.WINDOWS.getBindings(mt, fd, false, LinkerOptions.forDowncall(fd, firstVariadicArg(1)));

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fdExpected);

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, int.class) },
            { vmStore(r1, int.class) },
            { vmStore(r2, float.class) },
            { vmStore(r3, double.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testWindowsArgsInRegsAndOnStack() {
        MethodType mt = MethodType.methodType(void.class, double.class, int.class, float.class,
                                              double.class, float.class, float.class, double.class,
                                              float.class, float.class, float.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_DOUBLE, C_INT, C_FLOAT,
                                              C_DOUBLE, C_FLOAT, C_FLOAT, C_DOUBLE,
                                              C_FLOAT, C_FLOAT, C_FLOAT, C_INT);
        CallArranger.Bindings bindings = CallArranger.WINDOWS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(v0, double.class) },
            { vmStore(r0, int.class) },
            { vmStore(v1, float.class) },
            { vmStore(v2, double.class) },
            { vmStore(v3, float.class) },
            { vmStore(v4, float.class) },
            { vmStore(v5, double.class) },
            { vmStore(v6, float.class) },
            { vmStore(v7, float.class) },
            { vmStore(stackStorage((short) 4, 0), float.class) },
            { vmStore(r1, int.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testWindowsVarArgsInRegsAndOnStack() {
        MethodType mt = MethodType.methodType(void.class, double.class, int.class, float.class,
                                              double.class, float.class, float.class, double.class,
                                              float.class, float.class, float.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_DOUBLE, C_INT, C_FLOAT,
                                              C_DOUBLE, C_FLOAT, C_FLOAT, C_DOUBLE,
                                              C_FLOAT, C_FLOAT, C_FLOAT);
        FunctionDescriptor fdExpected = FunctionDescriptor.ofVoid(ADDRESS, C_DOUBLE, C_INT, C_FLOAT, C_DOUBLE, C_FLOAT, C_FLOAT, C_DOUBLE, C_FLOAT, C_FLOAT, C_FLOAT);
        CallArranger.Bindings bindings = CallArranger.WINDOWS.getBindings(mt, fd, false, LinkerOptions.forDowncall(fd, firstVariadicArg(1)));

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fdExpected);

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, double.class) },
            { vmStore(r1, int.class) },
            { vmStore(r2, float.class) },
            { vmStore(r3, double.class) },
            { vmStore(r4, float.class) },
            { vmStore(r5, float.class) },
            { vmStore(r6, double.class) },
            { vmStore(r7, float.class) },
            { vmStore(stackStorage((short) 4, 0), float.class) },
            { vmStore(stackStorage((short) 4, 8), float.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testWindowsHfa4FloatsInFloatRegs() {
        MemoryLayout struct = MemoryLayout.structLayout(C_FLOAT, C_FLOAT, C_FLOAT, C_FLOAT);

        MethodType mt = MethodType.methodType(void.class, MemorySegment.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct, C_INT);
        CallArranger.Bindings bindings = CallArranger.WINDOWS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            {
                dup(),
                bufferLoad(0, float.class),
                vmStore(v0, float.class),
                dup(),
                bufferLoad(4, float.class),
                vmStore(v1, float.class),
                dup(),
                bufferLoad(8, float.class),
                vmStore(v2, float.class),
                bufferLoad(12, float.class),
                vmStore(v3, float.class),
            },
            { vmStore(r0, int.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testWindowsVariadicHfa4FloatsInIntRegs() {
        MemoryLayout struct = MemoryLayout.structLayout(C_FLOAT, C_FLOAT, C_FLOAT, C_FLOAT);

        MethodType mt = MethodType.methodType(void.class, MemorySegment.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct, C_INT);
        CallArranger.Bindings bindings = CallArranger.WINDOWS.getBindings(mt, fd, false, LinkerOptions.forDowncall(fd, firstVariadicArg(0)));

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            {
                dup(),
                bufferLoad(0, long.class),
                vmStore(r0, long.class),
                bufferLoad(8, long.class),
                vmStore(r1, long.class),
            },
            { vmStore(r2, int.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testWindowsHfa2DoublesInFloatRegs() {
        MemoryLayout struct = MemoryLayout.structLayout(C_DOUBLE, C_DOUBLE);

        MethodType mt = MethodType.methodType(
            void.class, MemorySegment.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct, C_INT);
        CallArranger.Bindings bindings = CallArranger.WINDOWS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            {
                dup(),
                bufferLoad(0, double.class),
                vmStore(v0, double.class),
                bufferLoad(8, double.class),
                vmStore(v1, double.class),
            },
            { vmStore(r0, int.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testWindowsVariadicHfa2DoublesInIntRegs() {
        MemoryLayout struct = MemoryLayout.structLayout(C_DOUBLE, C_DOUBLE);

        MethodType mt = MethodType.methodType(
            void.class, MemorySegment.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct, C_INT);
        CallArranger.Bindings bindings = CallArranger.WINDOWS.getBindings(mt, fd, false, LinkerOptions.forDowncall(fd, firstVariadicArg(0)));

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            {
                dup(),
                bufferLoad(0, long.class),
                vmStore(r0, long.class),
                bufferLoad(8, long.class),
                vmStore(r1, long.class),
            },
            { vmStore(r2, int.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testWindowsHfa3DoublesInFloatRegs() {
        MemoryLayout struct = MemoryLayout.structLayout(C_DOUBLE, C_DOUBLE, C_DOUBLE);

        MethodType mt = MethodType.methodType(
            void.class, MemorySegment.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct, C_INT);
        CallArranger.Bindings bindings = CallArranger.WINDOWS.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            {
                dup(),
                bufferLoad(0, double.class),
                vmStore(v0, double.class),
                dup(),
                bufferLoad(8, double.class),
                vmStore(v1, double.class),
                bufferLoad(16, double.class),
                vmStore(v2, double.class),
            },
            { vmStore(r0, int.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testWindowsVariadicHfa3DoublesAsReferenceStruct() {
        MemoryLayout struct = MemoryLayout.structLayout(C_DOUBLE, C_DOUBLE, C_DOUBLE);

        MethodType mt = MethodType.methodType(
            void.class, MemorySegment.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct, C_INT);
        CallArranger.Bindings bindings = CallArranger.WINDOWS.getBindings(mt, fd, false, LinkerOptions.forDowncall(fd, firstVariadicArg(0)));

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { copy(struct), unboxAddress(), vmStore(r0, long.class) },
            { vmStore(r1, int.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }
}
