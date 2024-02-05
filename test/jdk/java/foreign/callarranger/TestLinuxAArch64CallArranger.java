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
 * @run testng TestLinuxAArch64CallArranger
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

public class TestLinuxAArch64CallArranger extends CallArrangerTestBase {

    private static final VMStorage TARGET_ADDRESS_STORAGE = StubLocations.TARGET_ADDRESS.storage(StorageType.PLACEHOLDER);
    private static final VMStorage RETURN_BUFFER_STORAGE = StubLocations.RETURN_BUFFER.storage(StorageType.PLACEHOLDER);

    @Test
    public void testEmpty() {
        MethodType mt = MethodType.methodType(void.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid();
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

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
    public void testInteger() {
        MethodType mt = MethodType.methodType(void.class,
                int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class,
                int.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_INT, C_INT, C_INT, C_INT,
                C_INT, C_INT, C_INT, C_INT,
                C_INT, C_INT);
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

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
            { vmStore(stackStorage((short) 4, 8), int.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testTwoIntTwoFloat() {
        MethodType mt = MethodType.methodType(void.class,
                int.class, int.class, float.class, float.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                C_INT, C_INT, C_FLOAT, C_FLOAT);
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, int.class) },
            { vmStore(r1, int.class) },
            { vmStore(v0, float.class) },
            { vmStore(v1, float.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test(dataProvider = "structs")
    public void testStruct(MemoryLayout struct, Binding[] expectedBindings) {
        MethodType mt = MethodType.methodType(void.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct);
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            expectedBindings
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @DataProvider
    public static Object[][] structs() {
        MemoryLayout struct2 = MemoryLayout.structLayout(C_INT, C_INT, C_DOUBLE, C_INT);
        return new Object[][]{
            // struct s { int32_t a, b; double c; };
            { MemoryLayout.structLayout(C_INT, C_INT, C_DOUBLE), new Binding[] {
                dup(),
                    // s.a & s.b
                    bufferLoad(0, long.class), vmStore(r0, long.class),
                    // s.c --> note AArch64 passes this in an *integer* register
                    bufferLoad(8, long.class), vmStore(r1, long.class),
            }},
            // struct s { int32_t a, b; double c; int32_t d };
            { struct2, new Binding[] {
                copy(struct2),
                unboxAddress(),
                vmStore(r0, long.class)
            }},
            // struct s { int32_t a[2]; float b[2] };
            { MemoryLayout.structLayout(C_INT, C_INT, C_FLOAT, C_FLOAT), new Binding[] {
                dup(),
                    // s.a[0] & s.a[1]
                    bufferLoad(0, long.class), vmStore(r0, long.class),
                    // s.b[0] & s.b[1]
                    bufferLoad(8, long.class), vmStore(r1, long.class),
            }},
            // struct s { float a; /* padding */ double b };
            { MemoryLayout.structLayout(C_FLOAT, MemoryLayout.paddingLayout(4), C_DOUBLE),
              new Binding[] {
                dup(),
                // s.a
                bufferLoad(0, long.class), vmStore(r0, long.class),
                // s.b
                bufferLoad(8, long.class), vmStore(r1, long.class),
            }},
        };
    }

    @Test
    public void testMultipleStructs() {
        MemoryLayout struct1 = MemoryLayout.structLayout(C_INT, C_INT, C_DOUBLE, C_INT);
        MemoryLayout struct2 = MemoryLayout.structLayout(C_LONG, C_LONG, C_LONG);

        MethodType mt = MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct1, struct2, C_INT);
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            {
                copy(struct1),
                unboxAddress(),
                vmStore(r0, long.class)
            },
            {
                copy(struct2),
                unboxAddress(),
                vmStore(r1, long.class)
            },
            { vmStore(r2, int.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testReturnStruct1() {
        MemoryLayout struct = MemoryLayout.structLayout(C_LONG, C_LONG, C_FLOAT);

        MethodType mt = MethodType.methodType(MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.of(struct);
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

        assertTrue(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), FunctionDescriptor.ofVoid(ADDRESS, C_POINTER));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            {
                unboxAddress(),
                vmStore(r8, long.class)
            }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testReturnStruct2() {
        MemoryLayout struct = MemoryLayout.structLayout(C_LONG, C_LONG);

        MethodType mt = MethodType.methodType(MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.of(struct);
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(RETURN_BUFFER_STORAGE, long.class) },
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{
            allocate(struct),
            dup(),
            vmLoad(r0, long.class),
            bufferStore(0, long.class),
            dup(),
            vmLoad(r1, long.class),
            bufferStore(8, long.class),
        });
    }

    @Test
    public void testStructHFA1() {
        MemoryLayout hfa = MemoryLayout.structLayout(C_FLOAT, C_FLOAT);

        MethodType mt = MethodType.methodType(MemorySegment.class, float.class, int.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.of(hfa, C_FLOAT, C_INT, hfa);
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(RETURN_BUFFER_STORAGE, long.class) },
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(v0, float.class) },
            { vmStore(r0, int.class) },
            {
                dup(),
                bufferLoad(0, float.class),
                vmStore(v1, float.class),
                bufferLoad(4, float.class),
                vmStore(v2, float.class)
            }
        });

        checkReturnBindings(callingSequence, new Binding[]{
            allocate(hfa),
            dup(),
            vmLoad(v0, float.class),
            bufferStore(0, float.class),
            dup(),
            vmLoad(v1, float.class),
            bufferStore(4, float.class),
        });
    }

    @Test
    public void testStructHFA3() {
        MemoryLayout struct = MemoryLayout.structLayout(C_FLOAT, C_FLOAT, C_FLOAT);

        MethodType mt = MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct, struct, struct);
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

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
                bufferLoad(8, float.class),
                vmStore(v2, float.class)
            },
            {
                dup(),
                bufferLoad(0, float.class),
                vmStore(v3, float.class),
                dup(),
                bufferLoad(4, float.class),
                vmStore(v4, float.class),
                bufferLoad(8, float.class),
                vmStore(v5, float.class)
            },
            {
                dup(),
                bufferLoad(0, long.class),
                vmStore(stackStorage((short) 8, 0), long.class),
                bufferLoad(8, int.class),
                vmStore(stackStorage((short) 4, 8), int.class),
            }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testStructStackSpill() {
        // A large (> 16 byte) struct argument that is spilled to the
        // stack should be passed as a pointer to a copy and occupy one
        // stack slot.

        MemoryLayout struct = MemoryLayout.structLayout(C_INT, C_INT, C_DOUBLE, C_INT);

        MethodType mt = MethodType.methodType(
            void.class, MemorySegment.class, MemorySegment.class, int.class, int.class,
            int.class, int.class, int.class, int.class, MemorySegment.class, int.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
            struct, struct, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, struct, C_INT);
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { copy(struct), unboxAddress(), vmStore(r0, long.class) },
            { copy(struct), unboxAddress(), vmStore(r1, long.class) },
            { vmStore(r2, int.class) },
            { vmStore(r3, int.class) },
            { vmStore(r4, int.class) },
            { vmStore(r5, int.class) },
            { vmStore(r6, int.class) },
            { vmStore(r7, int.class) },
            { copy(struct), unboxAddress(), vmStore(stackStorage((short) 8, 0), long.class) },
            { vmStore(stackStorage((short) 4, 8), int.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testVarArgsInRegs() {
        MethodType mt = MethodType.methodType(void.class, int.class, int.class, float.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_INT, C_INT, C_FLOAT);
        FunctionDescriptor fdExpected = FunctionDescriptor.ofVoid(ADDRESS, C_INT, C_INT, C_FLOAT);
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false, LinkerOptions.forDowncall(fd, firstVariadicArg(1)));

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fdExpected);

        // This is identical to the non-variadic calling sequence
        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(r0, int.class) },
            { vmStore(r1, int.class) },
            { vmStore(v0, float.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testFloatArrayStruct() {
        // should be classified as HFA
        StructLayout S10 = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(4, C_DOUBLE)
        );
        MethodType mt = MethodType.methodType(MemorySegment.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.of(S10, S10);
        FunctionDescriptor fdExpected = FunctionDescriptor.of(S10, ADDRESS, ADDRESS, S10); // uses return buffer
        CallArranger.Bindings bindings = CallArranger.LINUX.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fdExpected);

        // This is identical to the non-variadic calling sequence
        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(RETURN_BUFFER_STORAGE, long.class) },
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { dup(),
                bufferLoad(0, double.class),
                vmStore(v0, double.class),
              dup(),
                bufferLoad(8, double.class),
                vmStore(v1, double.class),
              dup(),
                bufferLoad(16, double.class),
                vmStore(v2, double.class),
                bufferLoad(24, double.class),
                vmStore(v3, double.class) },
        });

        checkReturnBindings(callingSequence, new Binding[]{
            allocate(S10),
              dup(),
                 vmLoad(v0, double.class),
                 bufferStore(0, double.class),
              dup(),
                 vmLoad(v1, double.class),
                 bufferStore(8, double.class),
              dup(),
                 vmLoad(v2, double.class),
                 bufferStore(16, double.class),
              dup(),
                 vmLoad(v3, double.class),
                 bufferStore(24, double.class),
        });
    }
}
