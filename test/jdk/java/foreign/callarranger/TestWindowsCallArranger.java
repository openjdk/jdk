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
 *          java.base/jdk.internal.foreign.abi.x64
 *          java.base/jdk.internal.foreign.abi.x64.windows
 * @build CallArrangerTestBase
 * @run testng TestWindowsCallArranger
 */

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.StubLocations;
import jdk.internal.foreign.abi.VMStorage;
import jdk.internal.foreign.abi.x64.windows.CallArranger;
import org.testng.annotations.Test;

import java.lang.invoke.MethodType;

import static java.lang.foreign.Linker.Option.firstVariadicArg;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static jdk.internal.foreign.abi.Binding.*;
import static jdk.internal.foreign.abi.Binding.copy;
import static jdk.internal.foreign.abi.x64.X86_64Architecture.*;
import static jdk.internal.foreign.abi.x64.X86_64Architecture.Regs.*;
import static platform.PlatformLayouts.Win64.*;

import static org.testng.Assert.*;

public class TestWindowsCallArranger extends CallArrangerTestBase {

    private static final short STACK_SLOT_SIZE = 8;
    private static final VMStorage TARGET_ADDRESS_STORAGE = StubLocations.TARGET_ADDRESS.storage(StorageType.PLACEHOLDER);

    @Test
    public void testEmpty() {
        MethodType mt = MethodType.methodType(void.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid();
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) }
        });
        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testIntegerRegs() {
        MethodType mt = MethodType.methodType(void.class, int.class, int.class, int.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_INT, C_INT, C_INT, C_INT);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(rcx, int.class) },
            { vmStore(rdx, int.class) },
            { vmStore(r8, int.class) },
            { vmStore(r9, int.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testDoubleRegs() {
        MethodType mt = MethodType.methodType(void.class, double.class, double.class, double.class, double.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(xmm0, double.class) },
            { vmStore(xmm1, double.class) },
            { vmStore(xmm2, double.class) },
            { vmStore(xmm3, double.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testMixed() {
        MethodType mt = MethodType.methodType(void.class,
                long.class, long.class, float.class, float.class, long.class, long.class, float.class, float.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_LONG_LONG, C_LONG_LONG, C_FLOAT, C_FLOAT, C_LONG_LONG, C_LONG_LONG, C_FLOAT, C_FLOAT);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(rcx, long.class) },
            { vmStore(rdx, long.class) },
            { vmStore(xmm2, float.class) },
            { vmStore(xmm3, float.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 0), long.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 8), long.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 16), float.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 24), float.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testAbiExample() {
        MemoryLayout structLayout = MemoryLayout.structLayout(C_INT, C_INT, C_DOUBLE);
        MethodType mt = MethodType.methodType(void.class,
                int.class, int.class, MemorySegment.class, int.class, int.class,
                double.class, double.class, double.class, int.class, int.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_INT, C_INT, structLayout, C_INT, C_INT,
                C_DOUBLE, C_DOUBLE, C_DOUBLE, C_INT, C_INT, C_INT);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(rcx, int.class) },
            { vmStore(rdx, int.class) },
            {
                copy(structLayout),
                unboxAddress(),
                vmStore(r8, long.class)
            },
            { vmStore(r9, int.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 0), int.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 8), double.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 16), double.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 24), double.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 32), int.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 40), int.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 48), int.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testAbiExampleVarargs() {
        MethodType mt = MethodType.methodType(void.class,
                int.class, double.class, int.class, double.class, double.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_INT, C_DOUBLE, C_INT, C_DOUBLE, C_DOUBLE);
        FunctionDescriptor fdExpected = FunctionDescriptor.ofVoid(
                ADDRESS, C_INT, C_DOUBLE, C_INT, C_DOUBLE, C_DOUBLE);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false, LinkerOptions.forDowncall(fd, firstVariadicArg(2)));

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fdExpected);

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(rcx, int.class) },
            { vmStore(xmm1, double.class) },
            { vmStore(r8, int.class) },
            { dup(), vmStore(r9, double.class), vmStore(xmm3, double.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 0), double.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    /**
     * struct s {
     *   uint64_t u0;
     * } s;
     *
     * void m(struct s s);
     *
     * m(s);
     */
    @Test
    public void testStructRegister() {
        MemoryLayout struct = MemoryLayout.structLayout(C_LONG_LONG);

        MethodType mt = MethodType.methodType(void.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { bufferLoad(0, long.class), vmStore(rcx, long.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    /**
     * struct s {
     *   uint64_t u0, u1;
     * } s;
     *
     * void m(struct s s);
     *
     * m(s);
     */
    @Test
    public void testStructReference() {
        MemoryLayout struct = MemoryLayout.structLayout(C_LONG_LONG, C_LONG_LONG);

        MethodType mt = MethodType.methodType(void.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            {
                copy(struct),
                unboxAddress(),
                vmStore(rcx, long.class)
            }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    /**
     * typedef void (*f)(void);
     *
     * void m(f f);
     * void f_impl(void);
     *
     * m(f_impl);
     */
    @Test
    public void testMemoryAddress() {
        MethodType mt = MethodType.methodType(void.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_POINTER);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { unboxAddress(), vmStore(rcx, long.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testReturnRegisterStruct() {
        MemoryLayout struct = MemoryLayout.structLayout(C_LONG_LONG);

        MethodType mt = MethodType.methodType(MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.of(struct);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
        });

        checkReturnBindings(callingSequence,
            new Binding[]{ allocate(struct),
                dup(),
                vmLoad(rax, long.class),
                bufferStore(0, long.class) });
    }

    @Test
    public void testIMR() {
        MemoryLayout struct = MemoryLayout.structLayout(C_LONG_LONG, C_LONG_LONG);

        MethodType mt = MethodType.methodType(MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.of(struct);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertTrue(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), FunctionDescriptor.ofVoid(ADDRESS, C_POINTER));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { unboxAddress(), vmStore(rcx, long.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testStackStruct() {
        MemoryLayout struct = MemoryLayout.structLayout(C_POINTER, C_DOUBLE, C_INT);

        MethodType mt = MethodType.methodType(void.class,
            MemorySegment.class, int.class, double.class, MemorySegment.class,
            MemorySegment.class, int.class, double.class, MemorySegment.class,
            MemorySegment.class, int.class, double.class, MemorySegment.class,
            MemorySegment.class, int.class, double.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
            struct, C_INT, C_DOUBLE, C_POINTER,
            struct, C_INT, C_DOUBLE, C_POINTER,
            struct, C_INT, C_DOUBLE, C_POINTER,
            struct, C_INT, C_DOUBLE, C_POINTER);
        CallArranger.Bindings bindings = CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { copy(struct), unboxAddress(), vmStore(rcx, long.class) },
            { vmStore(rdx, int.class) },
            { vmStore(xmm2, double.class) },
            { unboxAddress(), vmStore(r9, long.class) },
            { copy(struct), unboxAddress(), vmStore(stackStorage(STACK_SLOT_SIZE, 0), long.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 8), int.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 16), double.class) },
            { unboxAddress(), vmStore(stackStorage(STACK_SLOT_SIZE, 24), long.class) },
            { copy(struct), unboxAddress(), vmStore(stackStorage(STACK_SLOT_SIZE, 32), long.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 40), int.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 48), double.class) },
            { unboxAddress(), vmStore(stackStorage(STACK_SLOT_SIZE, 56), long.class) },
            { copy(struct), unboxAddress(), vmStore(stackStorage(STACK_SLOT_SIZE, 64), long.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 72), int.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 80), double.class) },
            { unboxAddress(), vmStore(stackStorage(STACK_SLOT_SIZE, 88), long.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }
}
