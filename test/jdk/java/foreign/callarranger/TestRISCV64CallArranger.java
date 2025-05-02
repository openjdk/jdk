/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Institute of Software, Chinese Academy of Sciences.
 * All rights reserved.
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
 *
 */

/*
 * @test
 * @requires sun.arch.data.model == "64"
 * @compile platform/PlatformLayouts.java
 * @modules java.base/jdk.internal.foreign
 *          java.base/jdk.internal.foreign.abi
 *          java.base/jdk.internal.foreign.abi.riscv64
 *          java.base/jdk.internal.foreign.abi.riscv64.linux
 * @build CallArrangerTestBase
 * @run testng TestRISCV64CallArranger
 */

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.riscv64.linux.LinuxRISCV64CallArranger;
import jdk.internal.foreign.abi.StubLocations;
import jdk.internal.foreign.abi.VMStorage;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodType;

import static java.lang.foreign.Linker.Option.firstVariadicArg;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static jdk.internal.foreign.abi.Binding.*;
import static jdk.internal.foreign.abi.riscv64.RISCV64Architecture.*;
import static jdk.internal.foreign.abi.riscv64.RISCV64Architecture.Regs.*;
import static platform.PlatformLayouts.RISCV64.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestRISCV64CallArranger extends CallArrangerTestBase {

    private static final short STACK_SLOT_SIZE = 8;
    private static final VMStorage TARGET_ADDRESS_STORAGE = StubLocations.TARGET_ADDRESS.storage(StorageType.PLACEHOLDER);
    private static final VMStorage RETURN_BUFFER_STORAGE = StubLocations.RETURN_BUFFER.storage(StorageType.PLACEHOLDER);

    @Test
    public void testEmpty() {
        MethodType mt = MethodType.methodType(void.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid();
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

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
            byte.class, short.class, int.class, int.class,
            int.class, int.class, long.class, int.class,
            int.class, byte.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(
            C_CHAR, C_SHORT, C_INT, C_INT,
            C_INT, C_INT, C_LONG, C_INT,
            C_INT, C_CHAR);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { cast(byte.class, int.class), vmStore(x10, int.class) },
            { cast(short.class, int.class), vmStore(x11, int.class) },
            { vmStore(x12, int.class) },
            { vmStore(x13, int.class) },
            { vmStore(x14, int.class) },
            { vmStore(x15, int.class) },
            { vmStore(x16, long.class) },
            { vmStore(x17, int.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 0), int.class) },
            { cast(byte.class, int.class), vmStore(stackStorage(STACK_SLOT_SIZE, 8), int.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testTwoIntTwoFloat() {
        MethodType mt = MethodType.methodType(void.class, int.class, int.class, float.class, float.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_INT, C_INT, C_FLOAT, C_FLOAT);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(x10, int.class) },
            { vmStore(x11, int.class) },
            { vmStore(f10, float.class) },
            { vmStore(f11, float.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test(dataProvider = "structs")
    public void testStruct(MemoryLayout struct, Binding[] expectedBindings) {
        MethodType mt = MethodType.methodType(void.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

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
        MemoryLayout struct1 = MemoryLayout.structLayout(C_INT, C_INT, C_DOUBLE, C_INT);
        return new Object[][]{
            // struct s { void* a; double c; };
            {
                MemoryLayout.structLayout(C_POINTER, C_DOUBLE),
                new Binding[]{
                    dup(),
                    bufferLoad(0, long.class), vmStore(x10, long.class),
                    bufferLoad(8, long.class), vmStore(x11, long.class)
                }
            },
            // struct s { int32_t a, b; double c; };
            { MemoryLayout.structLayout(C_INT, C_INT, C_DOUBLE),
                new Binding[]{
                    dup(),
                    // s.a & s.b
                    bufferLoad(0, long.class), vmStore(x10, long.class),
                    // s.c
                    bufferLoad(8, long.class), vmStore(x11, long.class)
                }
            },
            // struct s { int32_t a, b; double c; int32_t d; };
            { struct1,
                new Binding[]{
                    copy(struct1),
                    unboxAddress(),
                    vmStore(x10, long.class)
                }
            },
            // struct s { int32_t a[1]; float b[1]; };
            { MemoryLayout.structLayout(MemoryLayout.sequenceLayout(1, C_INT),
                MemoryLayout.sequenceLayout(1, C_FLOAT)),
                new Binding[]{
                    dup(),
                    // s.a[0]
                    bufferLoad(0, int.class), vmStore(x10, int.class),
                    // s.b[0]
                    bufferLoad(4, float.class), vmStore(f10, float.class)
                }
            },
            // struct s { float a; /* padding */ double b };
            { MemoryLayout.structLayout(C_FLOAT, MemoryLayout.paddingLayout(4), C_DOUBLE),
                new Binding[]{
                    dup(),
                    // s.a
                    bufferLoad(0, float.class), vmStore(f10, float.class),
                    // s.b
                    bufferLoad(8, double.class), vmStore(f11, double.class),
                }
            }
        };
    }

    @Test
    public void testStructFA1() {
        MemoryLayout fa = MemoryLayout.structLayout(C_FLOAT, C_FLOAT);

        MethodType mt = MethodType.methodType(MemorySegment.class, float.class, int.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.of(fa, C_FLOAT, C_INT, fa);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(RETURN_BUFFER_STORAGE, long.class) },
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(f10, float.class) },
            { vmStore(x10, int.class) },
            {
                dup(),
                bufferLoad(0, float.class),
                vmStore(f11, float.class),
                bufferLoad(4, float.class),
                vmStore(f12, float.class)
            }
        });

        checkReturnBindings(callingSequence, new Binding[]{
            allocate(fa),
            dup(),
            vmLoad(f10, float.class),
            bufferStore(0, float.class),
            dup(),
            vmLoad(f11, float.class),
            bufferStore(4, float.class)
        });
    }

    @Test
    public void testStructFA2() {
        MemoryLayout fa = MemoryLayout.structLayout(C_FLOAT, MemoryLayout.paddingLayout(4), C_DOUBLE);

        MethodType mt = MethodType.methodType(MemorySegment.class, float.class, int.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.of(fa, C_FLOAT, C_INT, fa);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(RETURN_BUFFER_STORAGE, long.class) },
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(f10, float.class) },
            { vmStore(x10, int.class) },
            {
                dup(),
                bufferLoad(0, float.class),
                vmStore(f11, float.class),
                bufferLoad(8, double.class),
                vmStore(f12, double.class)
            }
        });

        checkReturnBindings(callingSequence, new Binding[]{
            allocate(fa),
            dup(),
            vmLoad(f10, float.class),
            bufferStore(0, float.class),
            dup(),
            vmLoad(f11, double.class),
            bufferStore(8, double.class)
        });
    }

    @Test
    void spillFloatingPointStruct() {
        MemoryLayout struct = MemoryLayout.structLayout(C_FLOAT, C_FLOAT);
        // void f(float, float, float, float, float, float, float, struct)
        MethodType mt = MethodType.methodType(void.class, float.class, float.class,
            float.class, float.class, float.class,
            float.class, float.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_FLOAT, C_FLOAT, C_FLOAT, C_FLOAT,
            C_FLOAT, C_FLOAT, C_FLOAT, struct);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(f10, float.class) },
            { vmStore(f11, float.class) },
            { vmStore(f12, float.class) },
            { vmStore(f13, float.class) },
            { vmStore(f14, float.class) },
            { vmStore(f15, float.class) },
            { vmStore(f16, float.class) },
            {
                bufferLoad(0, long.class),
                vmStore(x10, long.class)
            }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testStructBoth() {
        MemoryLayout struct = MemoryLayout.structLayout(C_INT, C_FLOAT);

        MethodType mt = MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(struct, struct, struct);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            {
                dup(),
                bufferLoad(0, int.class),
                vmStore(x10, int.class),
                bufferLoad(4, float.class),
                vmStore(f10, float.class)
            },
            {
                dup(),
                bufferLoad(0, int.class),
                vmStore(x11, int.class),
                bufferLoad(4, float.class),
                vmStore(f11, float.class)
            },
            {
                dup(),
                bufferLoad(0, int.class),
                vmStore(x12, int.class),
                bufferLoad(4, float.class),
                vmStore(f12, float.class)
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
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fd.insertArgumentLayouts(0, ADDRESS));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { copy(struct), unboxAddress(), vmStore(x10, long.class) },
            { copy(struct), unboxAddress(), vmStore(x11, long.class) },
            { vmStore(x12, int.class) },
            { vmStore(x13, int.class) },
            { vmStore(x14, int.class) },
            { vmStore(x15, int.class) },
            { vmStore(x16, int.class) },
            { vmStore(x17, int.class) },
            { copy(struct), unboxAddress(), vmStore(stackStorage(STACK_SLOT_SIZE, 0), long.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 8), int.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testVarArgsInRegs() {
        MethodType mt = MethodType.methodType(void.class, int.class, int.class, float.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_INT, C_INT, C_FLOAT);
        FunctionDescriptor fdExpected = FunctionDescriptor.ofVoid(ADDRESS, C_INT, C_INT, C_FLOAT);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false, LinkerOptions.forDowncall(fd, firstVariadicArg(1)));

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fdExpected);

        // This is identical to the non-variadic calling sequence
        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(x10, int.class) },
            { vmStore(x11, int.class) },
            { vmStore(x12, float.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testVarArgsLong() {
        MethodType mt = MethodType.methodType(void.class, int.class, int.class, int.class, double.class,
            double.class, long.class, long.class, int.class,
            double.class, double.class, long.class);
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_INT, C_INT, C_INT, C_DOUBLE, C_DOUBLE,
            C_LONG, C_LONG, C_INT, C_DOUBLE,
            C_DOUBLE, C_LONG);
        FunctionDescriptor fdExpected = FunctionDescriptor.ofVoid(ADDRESS, C_INT, C_INT, C_INT, C_DOUBLE,
            C_DOUBLE, C_LONG, C_LONG, C_INT,
            C_DOUBLE, C_DOUBLE, C_LONG);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false, LinkerOptions.forDowncall(fd, firstVariadicArg(1)));

        assertFalse(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(), mt.insertParameterTypes(0, MemorySegment.class));
        assertEquals(callingSequence.functionDesc(), fdExpected);

        // This is identical to the non-variadic calling sequence
        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { vmStore(x10, int.class) },
            { vmStore(x11, int.class) },
            { vmStore(x12, int.class) },
            { vmStore(x13, double.class) },
            { vmStore(x14, double.class) },
            { vmStore(x15, long.class) },
            { vmStore(x16, long.class) },
            { vmStore(x17, int.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 0), double.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 8), double.class) },
            { vmStore(stackStorage(STACK_SLOT_SIZE, 16), long.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testReturnStruct1() {
        MemoryLayout struct = MemoryLayout.structLayout(C_LONG, C_LONG, C_FLOAT);

        MethodType mt = MethodType.methodType(MemorySegment.class, int.class, int.class, float.class);
        FunctionDescriptor fd = FunctionDescriptor.of(struct, C_INT, C_INT, C_FLOAT);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

        assertTrue(bindings.isInMemoryReturn());
        CallingSequence callingSequence = bindings.callingSequence();
        assertEquals(callingSequence.callerMethodType(),
            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class,
                int.class, int.class, float.class));
        assertEquals(callingSequence.functionDesc(),
            FunctionDescriptor.ofVoid(ADDRESS, C_POINTER, C_INT, C_INT, C_FLOAT));

        checkArgumentBindings(callingSequence, new Binding[][]{
            { unboxAddress(), vmStore(TARGET_ADDRESS_STORAGE, long.class) },
            { unboxAddress(), vmStore(x10, long.class) },
            { vmStore(x11, int.class) },
            { vmStore(x12, int.class) },
            { vmStore(f10, float.class) }
        });

        checkReturnBindings(callingSequence, new Binding[]{});
    }

    @Test
    public void testReturnStruct2() {
        MemoryLayout struct = MemoryLayout.structLayout(C_LONG, C_LONG);

        MethodType mt = MethodType.methodType(MemorySegment.class);
        FunctionDescriptor fd = FunctionDescriptor.of(struct);
        LinuxRISCV64CallArranger.Bindings bindings = LinuxRISCV64CallArranger.getBindings(mt, fd, false);

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
            vmLoad(x10, long.class),
            bufferStore(0, long.class),
            dup(),
            vmLoad(x11, long.class),
            bufferStore(8, long.class)
        });
    }
}
