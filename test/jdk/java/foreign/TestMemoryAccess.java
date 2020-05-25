/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

/*
 * @test
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestMemoryAccess
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestMemoryAccess
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestMemoryAccess
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestMemoryAccess
 */

import jdk.incubator.foreign.GroupLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayout.PathElement;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SequenceLayout;
import jdk.incubator.foreign.ValueLayout;
import jdk.incubator.foreign.MemoryAddress;
import java.lang.invoke.VarHandle;
import java.util.function.Function;

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TestMemoryAccess {

    @Test(dataProvider = "elements")
    public void testAccess(Function<MemorySegment, MemorySegment> viewFactory, ValueLayout elemLayout, Class<?> carrier, Checker checker) {
        ValueLayout layout = elemLayout.withName("elem");
        testAccessInternal(viewFactory, layout, layout.varHandle(carrier), checker);
    }

    @Test(dataProvider = "elements")
    public void testPaddedAccessByName(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, Checker checker) {
        GroupLayout layout = MemoryLayout.ofStruct(MemoryLayout.ofPaddingBits(elemLayout.bitSize()), elemLayout.withName("elem"));
        testAccessInternal(viewFactory, layout, layout.varHandle(carrier, PathElement.groupElement("elem")), checker);
    }

    @Test(dataProvider = "elements")
    public void testPaddedAccessByIndexSeq(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, Checker checker) {
        SequenceLayout layout = MemoryLayout.ofSequence(2, elemLayout);
        testAccessInternal(viewFactory, layout, layout.varHandle(carrier, PathElement.sequenceElement(1)), checker);
    }

    @Test(dataProvider = "arrayElements")
    public void testArrayAccess(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, ArrayChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(10, elemLayout.withName("elem"));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(carrier, PathElement.sequenceElement()), checker);
    }

    @Test(dataProvider = "arrayElements")
    public void testPaddedArrayAccessByName(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, ArrayChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(10, MemoryLayout.ofStruct(MemoryLayout.ofPaddingBits(elemLayout.bitSize()), elemLayout.withName("elem")));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(carrier, MemoryLayout.PathElement.sequenceElement(), MemoryLayout.PathElement.groupElement("elem")), checker);
    }

    @Test(dataProvider = "arrayElements")
    public void testPaddedArrayAccessByIndexSeq(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, ArrayChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(10, MemoryLayout.ofSequence(2, elemLayout));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(carrier, PathElement.sequenceElement(), MemoryLayout.PathElement.sequenceElement(1)), checker);
    }

    private void testAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout layout, VarHandle handle, Checker checker) {
        MemoryAddress outer_address;
        try (MemorySegment segment = viewFactory.apply(MemorySegment.allocateNative(layout))) {
            boolean isRO = !segment.hasAccessModes(MemorySegment.WRITE);
            MemoryAddress addr = segment.baseAddress();
            try {
                checker.check(handle, addr);
                if (isRO) {
                    throw new AssertionError(); //not ok, memory should be immutable
                }
            } catch (UnsupportedOperationException ex) {
                if (!isRO) {
                    throw new AssertionError(); //we should not have failed!
                }
                return;
            }
            try {
                checker.check(handle, addr.addOffset(layout.byteSize()));
                throw new AssertionError(); //not ok, out of bounds
            } catch (IndexOutOfBoundsException ex) {
                //ok, should fail (out of bounds)
            }
            outer_address = addr; //leak!
        }
        try {
            checker.check(handle, outer_address);
            throw new AssertionError(); //not ok, scope is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (scope is closed)
        }
    }

    private void testArrayAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, SequenceLayout seq, VarHandle handle, ArrayChecker checker) {
        MemoryAddress outer_address;
        try (MemorySegment segment = viewFactory.apply(MemorySegment.allocateNative(seq))) {
            boolean isRO = !segment.hasAccessModes(MemorySegment.WRITE);
            MemoryAddress addr = segment.baseAddress();
            try {
                for (int i = 0; i < seq.elementCount().getAsLong(); i++) {
                    checker.check(handle, addr, i);
                }
                if (isRO) {
                    throw new AssertionError(); //not ok, memory should be immutable
                }
            } catch (UnsupportedOperationException ex) {
                if (!isRO) {
                    throw new AssertionError(); //we should not have failed!
                }
                return;
            }
            try {
                checker.check(handle, addr, seq.elementCount().getAsLong());
                throw new AssertionError(); //not ok, out of bounds
            } catch (IndexOutOfBoundsException ex) {
                //ok, should fail (out of bounds)
            }
            outer_address = addr; //leak!
        }
        try {
            checker.check(handle, outer_address, 0);
            throw new AssertionError(); //not ok, scope is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (scope is closed)
        }
    }

    @Test(dataProvider = "matrixElements")
    public void testMatrixAccess(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, MatrixChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(20,
                MemoryLayout.ofSequence(10, elemLayout.withName("elem")));
        testMatrixAccessInternal(viewFactory, seq, seq.varHandle(carrier,
                PathElement.sequenceElement(), PathElement.sequenceElement()), checker);
    }

    @Test(dataProvider = "matrixElements")
    public void testPaddedMatrixAccessByName(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, MatrixChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(20,
                MemoryLayout.ofSequence(10, MemoryLayout.ofStruct(MemoryLayout.ofPaddingBits(elemLayout.bitSize()), elemLayout.withName("elem"))));
        testMatrixAccessInternal(viewFactory, seq,
                seq.varHandle(carrier,
                        PathElement.sequenceElement(), PathElement.sequenceElement(), PathElement.groupElement("elem")),
                checker);
    }

    @Test(dataProvider = "matrixElements")
    public void testPaddedMatrixAccessByIndexSeq(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, MatrixChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(20,
                MemoryLayout.ofSequence(10, MemoryLayout.ofSequence(2, elemLayout)));
        testMatrixAccessInternal(viewFactory, seq,
                seq.varHandle(carrier,
                        PathElement.sequenceElement(), PathElement.sequenceElement(), PathElement.sequenceElement(1)),
                checker);
    }

    @Test(dataProvider = "badCarriers",
          expectedExceptions = IllegalArgumentException.class)
    public void testBadCarriers(Class<?> carrier) {
        ValueLayout l = MemoryLayouts.BITS_32_LE.withName("elem");
        l.varHandle(carrier);
    }

    private void testMatrixAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, SequenceLayout seq, VarHandle handle, MatrixChecker checker) {
        MemoryAddress outer_address;
        try (MemorySegment segment = viewFactory.apply(MemorySegment.allocateNative(seq))) {
            boolean isRO = !segment.hasAccessModes(MemorySegment.WRITE);
            MemoryAddress addr = segment.baseAddress();
            try {
                for (int i = 0; i < seq.elementCount().getAsLong(); i++) {
                    for (int j = 0; j < ((SequenceLayout) seq.elementLayout()).elementCount().getAsLong(); j++) {
                        checker.check(handle, addr, i, j);
                    }
                }
                if (isRO) {
                    throw new AssertionError(); //not ok, memory should be immutable
                }
            } catch (UnsupportedOperationException ex) {
                if (!isRO) {
                    throw new AssertionError(); //we should not have failed!
                }
                return;
            }
            try {
                checker.check(handle, addr, seq.elementCount().getAsLong(),
                        ((SequenceLayout)seq.elementLayout()).elementCount().getAsLong());
                throw new AssertionError(); //not ok, out of bounds
            } catch (IndexOutOfBoundsException ex) {
                //ok, should fail (out of bounds)
            }
            outer_address = addr; //leak!
        }
        try {
            checker.check(handle, outer_address, 0, 0);
            throw new AssertionError(); //not ok, scope is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (scope is closed)
        }
    }

    static Function<MemorySegment, MemorySegment> ID = Function.identity();
    static Function<MemorySegment, MemorySegment> IMMUTABLE = ms -> ms.withAccessModes(MemorySegment.READ | MemorySegment.CLOSE);

    @DataProvider(name = "elements")
    public Object[][] createData() {
        return new Object[][] {
                //BE, RW
                { ID, MemoryLayouts.BITS_8_BE, byte.class, Checker.BYTE },
                { ID, MemoryLayouts.BITS_16_BE, short.class, Checker.SHORT },
                { ID, MemoryLayouts.BITS_16_BE, char.class, Checker.CHAR },
                { ID, MemoryLayouts.BITS_32_BE, int.class, Checker.INT },
                { ID, MemoryLayouts.BITS_64_BE, long.class, Checker.LONG },
                { ID, MemoryLayouts.BITS_32_BE, float.class, Checker.FLOAT },
                { ID, MemoryLayouts.BITS_64_BE, double.class, Checker.DOUBLE },
                //BE, RO
                { IMMUTABLE, MemoryLayouts.BITS_8_BE, byte.class, Checker.BYTE },
                { IMMUTABLE, MemoryLayouts.BITS_16_BE, short.class, Checker.SHORT },
                { IMMUTABLE, MemoryLayouts.BITS_16_BE, char.class, Checker.CHAR },
                { IMMUTABLE, MemoryLayouts.BITS_32_BE, int.class, Checker.INT },
                { IMMUTABLE, MemoryLayouts.BITS_64_BE, long.class, Checker.LONG },
                { IMMUTABLE, MemoryLayouts.BITS_32_BE, float.class, Checker.FLOAT },
                { IMMUTABLE, MemoryLayouts.BITS_64_BE, double.class, Checker.DOUBLE },
                //LE, RW
                { ID, MemoryLayouts.BITS_8_LE, byte.class, Checker.BYTE },
                { ID, MemoryLayouts.BITS_16_LE, short.class, Checker.SHORT },
                { ID, MemoryLayouts.BITS_16_LE, char.class, Checker.CHAR },
                { ID, MemoryLayouts.BITS_32_LE, int.class, Checker.INT },
                { ID, MemoryLayouts.BITS_64_LE, long.class, Checker.LONG },
                { ID, MemoryLayouts.BITS_32_LE, float.class, Checker.FLOAT },
                { ID, MemoryLayouts.BITS_64_LE, double.class, Checker.DOUBLE },
                //LE, RO
                { IMMUTABLE, MemoryLayouts.BITS_8_LE, byte.class, Checker.BYTE },
                { IMMUTABLE, MemoryLayouts.BITS_16_LE, short.class, Checker.SHORT },
                { IMMUTABLE, MemoryLayouts.BITS_16_LE, char.class, Checker.CHAR },
                { IMMUTABLE, MemoryLayouts.BITS_32_LE, int.class, Checker.INT },
                { IMMUTABLE, MemoryLayouts.BITS_64_LE, long.class, Checker.LONG },
                { IMMUTABLE, MemoryLayouts.BITS_32_LE, float.class, Checker.FLOAT },
                { IMMUTABLE, MemoryLayouts.BITS_64_LE, double.class, Checker.DOUBLE },
        };
    }

    interface Checker {
        void check(VarHandle handle, MemoryAddress addr);

        Checker BYTE = (handle, addr) -> {
            handle.set(addr, (byte)42);
            assertEquals(42, (byte)handle.get(addr));
        };

        Checker SHORT = (handle, addr) -> {
            handle.set(addr, (short)42);
            assertEquals(42, (short)handle.get(addr));
        };

        Checker CHAR = (handle, addr) -> {
            handle.set(addr, (char)42);
            assertEquals(42, (char)handle.get(addr));
        };

        Checker INT = (handle, addr) -> {
            handle.set(addr, 42);
            assertEquals(42, (int)handle.get(addr));
        };

        Checker LONG = (handle, addr) -> {
            handle.set(addr, (long)42);
            assertEquals(42, (long)handle.get(addr));
        };

        Checker FLOAT = (handle, addr) -> {
            handle.set(addr, (float)42);
            assertEquals((float)42, (float)handle.get(addr));
        };

        Checker DOUBLE = (handle, addr) -> {
            handle.set(addr, (double)42);
            assertEquals((double)42, (double)handle.get(addr));
        };
    }

    @DataProvider(name = "arrayElements")
    public Object[][] createArrayData() {
        return new Object[][] {
                //BE, RW
                { ID, MemoryLayouts.BITS_8_BE, byte.class, ArrayChecker.BYTE },
                { ID, MemoryLayouts.BITS_16_BE, short.class, ArrayChecker.SHORT },
                { ID, MemoryLayouts.BITS_16_BE, char.class, ArrayChecker.CHAR },
                { ID, MemoryLayouts.BITS_32_BE, int.class, ArrayChecker.INT },
                { ID, MemoryLayouts.BITS_64_BE, long.class, ArrayChecker.LONG },
                { ID, MemoryLayouts.BITS_32_BE, float.class, ArrayChecker.FLOAT },
                { ID, MemoryLayouts.BITS_64_BE, double.class, ArrayChecker.DOUBLE },
                //BE, RO
                { IMMUTABLE, MemoryLayouts.BITS_8_BE, byte.class, ArrayChecker.BYTE },
                { IMMUTABLE, MemoryLayouts.BITS_16_BE, short.class, ArrayChecker.SHORT },
                { IMMUTABLE, MemoryLayouts.BITS_16_BE, char.class, ArrayChecker.CHAR },
                { IMMUTABLE, MemoryLayouts.BITS_32_BE, int.class, ArrayChecker.INT },
                { IMMUTABLE, MemoryLayouts.BITS_64_BE, long.class, ArrayChecker.LONG },
                { IMMUTABLE, MemoryLayouts.BITS_32_BE, float.class, ArrayChecker.FLOAT },
                { IMMUTABLE, MemoryLayouts.BITS_64_BE, double.class, ArrayChecker.DOUBLE },
                //LE, RW
                { ID, MemoryLayouts.BITS_8_LE, byte.class, ArrayChecker.BYTE },
                { ID, MemoryLayouts.BITS_16_LE, short.class, ArrayChecker.SHORT },
                { ID, MemoryLayouts.BITS_16_LE, char.class, ArrayChecker.CHAR },
                { ID, MemoryLayouts.BITS_32_LE, int.class, ArrayChecker.INT },
                { ID, MemoryLayouts.BITS_64_LE, long.class, ArrayChecker.LONG },
                { ID, MemoryLayouts.BITS_32_LE, float.class, ArrayChecker.FLOAT },
                { ID, MemoryLayouts.BITS_64_LE, double.class, ArrayChecker.DOUBLE },
                //LE, RO
                { IMMUTABLE, MemoryLayouts.BITS_8_LE, byte.class, ArrayChecker.BYTE },
                { IMMUTABLE, MemoryLayouts.BITS_16_LE, short.class, ArrayChecker.SHORT },
                { IMMUTABLE, MemoryLayouts.BITS_16_LE, char.class, ArrayChecker.CHAR },
                { IMMUTABLE, MemoryLayouts.BITS_32_LE, int.class, ArrayChecker.INT },
                { IMMUTABLE, MemoryLayouts.BITS_64_LE, long.class, ArrayChecker.LONG },
                { IMMUTABLE, MemoryLayouts.BITS_32_LE, float.class, ArrayChecker.FLOAT },
                { IMMUTABLE, MemoryLayouts.BITS_64_LE, double.class, ArrayChecker.DOUBLE },
        };
    }

    interface ArrayChecker {
        void check(VarHandle handle, MemoryAddress addr, long index);

        ArrayChecker BYTE = (handle, addr, i) -> {
            handle.set(addr, i, (byte)i);
            assertEquals(i, (byte)handle.get(addr, i));
        };

        ArrayChecker SHORT = (handle, addr, i) -> {
            handle.set(addr, i, (short)i);
            assertEquals(i, (short)handle.get(addr, i));
        };

        ArrayChecker CHAR = (handle, addr, i) -> {
            handle.set(addr, i, (char)i);
            assertEquals(i, (char)handle.get(addr, i));
        };

        ArrayChecker INT = (handle, addr, i) -> {
            handle.set(addr, i, (int)i);
            assertEquals(i, (int)handle.get(addr, i));
        };

        ArrayChecker LONG = (handle, addr, i) -> {
            handle.set(addr, i, (long)i);
            assertEquals(i, (long)handle.get(addr, i));
        };

        ArrayChecker FLOAT = (handle, addr, i) -> {
            handle.set(addr, i, (float)i);
            assertEquals((float)i, (float)handle.get(addr, i));
        };

        ArrayChecker DOUBLE = (handle, addr, i) -> {
            handle.set(addr, i, (double)i);
            assertEquals((double)i, (double)handle.get(addr, i));
        };
    }

    @DataProvider(name = "matrixElements")
    public Object[][] createMatrixData() {
        return new Object[][] {
                //BE, RW
                { ID, MemoryLayouts.BITS_8_BE, byte.class, MatrixChecker.BYTE },
                { ID, MemoryLayouts.BITS_16_BE, short.class, MatrixChecker.SHORT },
                { ID, MemoryLayouts.BITS_16_BE, char.class, MatrixChecker.CHAR },
                { ID, MemoryLayouts.BITS_32_BE, int.class, MatrixChecker.INT },
                { ID, MemoryLayouts.BITS_64_BE, long.class, MatrixChecker.LONG },
                { ID, MemoryLayouts.BITS_32_BE, float.class, MatrixChecker.FLOAT },
                { ID, MemoryLayouts.BITS_64_BE, double.class, MatrixChecker.DOUBLE },
                //BE, RO
                { IMMUTABLE, MemoryLayouts.BITS_8_BE, byte.class, MatrixChecker.BYTE },
                { IMMUTABLE, MemoryLayouts.BITS_16_BE, short.class, MatrixChecker.SHORT },
                { IMMUTABLE, MemoryLayouts.BITS_16_BE, char.class, MatrixChecker.CHAR },
                { IMMUTABLE, MemoryLayouts.BITS_32_BE, int.class, MatrixChecker.INT },
                { IMMUTABLE, MemoryLayouts.BITS_64_BE, long.class, MatrixChecker.LONG },
                { IMMUTABLE, MemoryLayouts.BITS_32_BE, float.class, MatrixChecker.FLOAT },
                { IMMUTABLE, MemoryLayouts.BITS_64_BE, double.class, MatrixChecker.DOUBLE },
                //LE, RW
                { ID, MemoryLayouts.BITS_8_LE, byte.class, MatrixChecker.BYTE },
                { ID, MemoryLayouts.BITS_16_LE, short.class, MatrixChecker.SHORT },
                { ID, MemoryLayouts.BITS_16_LE, char.class, MatrixChecker.CHAR },
                { ID, MemoryLayouts.BITS_32_LE, int.class, MatrixChecker.INT },
                { ID, MemoryLayouts.BITS_64_LE, long.class, MatrixChecker.LONG },
                { ID, MemoryLayouts.BITS_32_LE, float.class, MatrixChecker.FLOAT },
                { ID, MemoryLayouts.BITS_64_LE, double.class, MatrixChecker.DOUBLE },
                //LE, RO
                { IMMUTABLE, MemoryLayouts.BITS_8_LE, byte.class, MatrixChecker.BYTE },
                { IMMUTABLE, MemoryLayouts.BITS_16_LE, short.class, MatrixChecker.SHORT },
                { IMMUTABLE, MemoryLayouts.BITS_16_LE, char.class, MatrixChecker.CHAR },
                { IMMUTABLE, MemoryLayouts.BITS_32_LE, int.class, MatrixChecker.INT },
                { IMMUTABLE, MemoryLayouts.BITS_64_LE, long.class, MatrixChecker.LONG },
                { IMMUTABLE, MemoryLayouts.BITS_32_LE, float.class, MatrixChecker.FLOAT },
                { IMMUTABLE, MemoryLayouts.BITS_64_LE, double.class, MatrixChecker.DOUBLE },
        };
    }

    interface MatrixChecker {
        void check(VarHandle handle, MemoryAddress addr, long row, long col);

        MatrixChecker BYTE = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (byte)(r + c));
            assertEquals(r + c, (byte)handle.get(addr, r, c));
        };

        MatrixChecker SHORT = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (short)(r + c));
            assertEquals(r + c, (short)handle.get(addr, r, c));
        };

        MatrixChecker CHAR = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (char)(r + c));
            assertEquals(r + c, (char)handle.get(addr, r, c));
        };

        MatrixChecker INT = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (int)(r + c));
            assertEquals(r + c, (int)handle.get(addr, r, c));
        };

        MatrixChecker LONG = (handle, addr, r, c) -> {
            handle.set(addr, r, c, r + c);
            assertEquals(r + c, (long)handle.get(addr, r, c));
        };

        MatrixChecker FLOAT = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (float)(r + c));
            assertEquals((float)(r + c), (float)handle.get(addr, r, c));
        };

        MatrixChecker DOUBLE = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (double)(r + c));
            assertEquals((double)(r + c), (double)handle.get(addr, r, c));
        };
    }

    @DataProvider(name = "badCarriers")
    public Object[][] createBadCarriers() {
        return new Object[][] {
                { void.class },
                { boolean.class },
                { Object.class },
                { int[].class }
        };
    }
}
