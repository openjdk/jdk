/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestMemoryAccess
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestMemoryAccess
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestMemoryAccess
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestMemoryAccess
 */

import java.lang.foreign.*;
import java.lang.foreign.MemoryLayout.PathElement;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.function.Function;

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TestMemoryAccess {

    @Test(dataProvider = "elements")
    public void testAccess(Function<MemorySegment, MemorySegment> viewFactory, ValueLayout elemLayout, Checker checker) {
        ValueLayout layout = elemLayout.withName("elem");
        testAccessInternal(viewFactory, layout, layout.varHandle(), checker);
    }

    @Test(dataProvider = "elements")
    public void testPaddedAccessByName(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Checker checker) {
        GroupLayout layout = MemoryLayout.structLayout(MemoryLayout.paddingLayout(elemLayout.byteSize()), elemLayout.withName("elem"));
        testAccessInternal(viewFactory, layout, layout.varHandle(PathElement.groupElement("elem")), checker);
    }

    @Test(dataProvider = "elements")
    public void testPaddedAccessByIndexSeq(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Checker checker) {
        SequenceLayout layout = MemoryLayout.sequenceLayout(2, elemLayout);
        testAccessInternal(viewFactory, layout, layout.varHandle(PathElement.sequenceElement(1)), checker);
    }

    @Test(dataProvider = "arrayElements")
    public void testArrayAccess(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, ArrayChecker checker) {
        SequenceLayout seq = MemoryLayout.sequenceLayout(10, elemLayout.withName("elem"));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(PathElement.sequenceElement()), checker);
    }

    @Test(dataProvider = "arrayElements")
    public void testPaddedArrayAccessByName(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, ArrayChecker checker) {
        SequenceLayout seq = MemoryLayout.sequenceLayout(10, MemoryLayout.structLayout(MemoryLayout.paddingLayout(elemLayout.byteSize()), elemLayout.withName("elem")));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(MemoryLayout.PathElement.sequenceElement(), MemoryLayout.PathElement.groupElement("elem")), checker);
    }

    @Test(dataProvider = "arrayElements")
    public void testPaddedArrayAccessByIndexSeq(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, ArrayChecker checker) {
        SequenceLayout seq = MemoryLayout.sequenceLayout(10, MemoryLayout.sequenceLayout(2, elemLayout));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(PathElement.sequenceElement(), MemoryLayout.PathElement.sequenceElement(1)), checker);
    }

    private void testAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout layout, VarHandle handle, Checker checker) {
        MemorySegment outer_segment;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = viewFactory.apply(arena.allocate(layout));
            boolean isRO = segment.isReadOnly();
            try {
                checker.check(handle, segment);
                if (isRO) {
                    throw new AssertionError(); //not ok, memory should be immutable
                }
            } catch (IllegalArgumentException ex) {
                if (!isRO) {
                    throw new AssertionError(); //we should not have failed!
                }
                return;
            }
            try {
                checker.check(handle, segment.asSlice(layout.byteSize()));
                throw new AssertionError(); //not ok, out of bounds
            } catch (IndexOutOfBoundsException ex) {
                //ok, should fail (out of bounds)
            }
            outer_segment = segment; //leak!
        }
        try {
            checker.check(handle, outer_segment);
            throw new AssertionError(); //not ok, session is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (session is closed)
        }
    }

    private void testArrayAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, SequenceLayout seq, VarHandle handle, ArrayChecker checker) {
        MemorySegment outer_segment;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = viewFactory.apply(arena.allocate(seq));
            boolean isRO = segment.isReadOnly();
            try {
                for (int i = 0; i < seq.elementCount(); i++) {
                    checker.check(handle, segment, i);
                }
                if (isRO) {
                    throw new AssertionError(); //not ok, memory should be immutable
                }
            } catch (IllegalArgumentException ex) {
                if (!isRO) {
                    throw new AssertionError(); //we should not have failed!
                }
                return;
            }
            try {
                checker.check(handle, segment, seq.elementCount());
                throw new AssertionError(); //not ok, out of bounds
            } catch (IndexOutOfBoundsException ex) {
                //ok, should fail (out of bounds)
            }
            outer_segment = segment; //leak!
        }
        try {
            checker.check(handle, outer_segment, 0);
            throw new AssertionError(); //not ok, session is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (session is closed)
        }
    }

    @Test(dataProvider = "matrixElements")
    public void testMatrixAccess(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, MatrixChecker checker) {
        SequenceLayout seq = MemoryLayout.sequenceLayout(20,
                MemoryLayout.sequenceLayout(10, elemLayout.withName("elem")));
        testMatrixAccessInternal(viewFactory, seq, seq.varHandle(
                PathElement.sequenceElement(), PathElement.sequenceElement()), checker);
    }

    @Test(dataProvider = "matrixElements")
    public void testPaddedMatrixAccessByName(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, MatrixChecker checker) {
        SequenceLayout seq = MemoryLayout.sequenceLayout(20,
                MemoryLayout.sequenceLayout(10, MemoryLayout.structLayout(MemoryLayout.paddingLayout(elemLayout.byteSize()), elemLayout.withName("elem"))));
        testMatrixAccessInternal(viewFactory, seq,
                seq.varHandle(
                        PathElement.sequenceElement(), PathElement.sequenceElement(), PathElement.groupElement("elem")),
                checker);
    }

    @Test(dataProvider = "matrixElements")
    public void testPaddedMatrixAccessByIndexSeq(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, MatrixChecker checker) {
        SequenceLayout seq = MemoryLayout.sequenceLayout(20,
                MemoryLayout.sequenceLayout(10, MemoryLayout.sequenceLayout(2, elemLayout)));
        testMatrixAccessInternal(viewFactory, seq,
                seq.varHandle(
                        PathElement.sequenceElement(), PathElement.sequenceElement(), PathElement.sequenceElement(1)),
                checker);
    }

    private void testMatrixAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, SequenceLayout seq, VarHandle handle, MatrixChecker checker) {
        MemorySegment outer_segment;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = viewFactory.apply(arena.allocate(seq));
            boolean isRO = segment.isReadOnly();
            try {
                for (int i = 0; i < seq.elementCount(); i++) {
                    for (int j = 0; j < ((SequenceLayout) seq.elementLayout()).elementCount(); j++) {
                        checker.check(handle, segment, i, j);
                    }
                }
                if (isRO) {
                    throw new AssertionError(); //not ok, memory should be immutable
                }
            } catch (IllegalArgumentException ex) {
                if (!isRO) {
                    throw new AssertionError(); //we should not have failed!
                }
                return;
            }
            try {
                checker.check(handle, segment, seq.elementCount(),
                        ((SequenceLayout)seq.elementLayout()).elementCount());
                throw new AssertionError(); //not ok, out of bounds
            } catch (IndexOutOfBoundsException ex) {
                //ok, should fail (out of bounds)
            }
            outer_segment = segment; //leak!
        }
        try {
            checker.check(handle, outer_segment, 0, 0);
            throw new AssertionError(); //not ok, session is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (session is closed)
        }
    }

    static Function<MemorySegment, MemorySegment> ID = Function.identity();
    static Function<MemorySegment, MemorySegment> IMMUTABLE = MemorySegment::asReadOnly;

    @DataProvider(name = "elements")
    public Object[][] createData() {
        return new Object[][] {
                //BE, RW
                { ID, ValueLayout.JAVA_BYTE, Checker.BYTE },
                { ID, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), Checker.SHORT },
                { ID, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN), Checker.CHAR },
                { ID, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), Checker.INT },
                { ID, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN), Checker.LONG },
                { ID, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN), Checker.FLOAT },
                { ID, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN), Checker.DOUBLE },
                //BE, RO
                { IMMUTABLE, ValueLayout.JAVA_BYTE, Checker.BYTE },
                { IMMUTABLE, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), Checker.SHORT },
                { IMMUTABLE, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN), Checker.CHAR },
                { IMMUTABLE, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), Checker.INT },
                { IMMUTABLE, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN), Checker.LONG },
                { IMMUTABLE, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN), Checker.FLOAT },
                { IMMUTABLE, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN), Checker.DOUBLE },
                //LE, RW
                { ID, ValueLayout.JAVA_BYTE, Checker.BYTE },
                { ID, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.SHORT },
                { ID, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.CHAR },
                { ID, ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.INT },
                { ID, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.LONG },
                { ID, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.FLOAT },
                { ID, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.DOUBLE },
                //LE, RO
                { IMMUTABLE, ValueLayout.JAVA_BYTE, Checker.BYTE },
                { IMMUTABLE, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.SHORT },
                { IMMUTABLE, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.CHAR },
                { IMMUTABLE, ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.INT },
                { IMMUTABLE, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.LONG },
                { IMMUTABLE, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.FLOAT },
                { IMMUTABLE, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN), Checker.DOUBLE },
        };
    }

    interface Checker {
        void check(VarHandle handle, MemorySegment segment);

        Checker BYTE = (handle, segment) -> {
            handle.set(segment, 0L, (byte)42);
            assertEquals(42, (byte)handle.get(segment, 0L));
        };

        Checker SHORT = (handle, segment) -> {
            handle.set(segment, 0L, (short)42);
            assertEquals(42, (short)handle.get(segment, 0L));
        };

        Checker CHAR = (handle, segment) -> {
            handle.set(segment, 0L, (char)42);
            assertEquals(42, (char)handle.get(segment, 0L));
        };

        Checker INT = (handle, segment) -> {
            handle.set(segment, 0L, 42);
            assertEquals(42, (int)handle.get(segment, 0L));
        };

        Checker LONG = (handle, segment) -> {
            handle.set(segment, 0L, (long)42);
            assertEquals(42, (long)handle.get(segment, 0L));
        };

        Checker FLOAT = (handle, segment) -> {
            handle.set(segment, 0L, (float)42);
            assertEquals((float)42, (float)handle.get(segment, 0L));
        };

        Checker DOUBLE = (handle, segment) -> {
            handle.set(segment, 0L, (double)42);
            assertEquals((double)42, (double)handle.get(segment, 0L));
        };
    }

    @DataProvider(name = "arrayElements")
    public Object[][] createArrayData() {
        return new Object[][] {
                //BE, RW
                { ID, ValueLayout.JAVA_BYTE, ArrayChecker.BYTE },
                { ID, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.SHORT },
                { ID, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.CHAR },
                { ID, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.INT },
                { ID, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.LONG },
                { ID, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.FLOAT },
                { ID, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.DOUBLE },
                //BE, RO
                { IMMUTABLE, ValueLayout.JAVA_BYTE, ArrayChecker.BYTE },
                { IMMUTABLE, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.SHORT },
                { IMMUTABLE, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.CHAR },
                { IMMUTABLE, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.INT },
                { IMMUTABLE, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.LONG },
                { IMMUTABLE, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.FLOAT },
                { IMMUTABLE, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN), ArrayChecker.DOUBLE },
                //LE, RW
                { ID, ValueLayout.JAVA_BYTE, ArrayChecker.BYTE },
                { ID, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.SHORT },
                { ID, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.CHAR },
                { ID, ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.INT },
                { ID, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.LONG },
                { ID, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.FLOAT },
                { ID, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.DOUBLE },
                //LE, RO
                { IMMUTABLE, ValueLayout.JAVA_BYTE, ArrayChecker.BYTE },
                { IMMUTABLE, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.SHORT },
                { IMMUTABLE, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.CHAR },
                { IMMUTABLE, ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.INT },
                { IMMUTABLE, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.LONG },
                { IMMUTABLE, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.FLOAT },
                { IMMUTABLE, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN), ArrayChecker.DOUBLE },
        };
    }

    interface ArrayChecker {
        void check(VarHandle handle, MemorySegment segment, long index);

        ArrayChecker BYTE = (handle, segment, i) -> {
            handle.set(segment, 0L, i, (byte)i);
            assertEquals(i, (byte)handle.get(segment, 0L, i));
        };

        ArrayChecker SHORT = (handle, segment, i) -> {
            handle.set(segment, 0L, i, (short)i);
            assertEquals(i, (short)handle.get(segment, 0L, i));
        };

        ArrayChecker CHAR = (handle, segment, i) -> {
            handle.set(segment, 0L, i, (char)i);
            assertEquals(i, (char)handle.get(segment, 0L, i));
        };

        ArrayChecker INT = (handle, segment, i) -> {
            handle.set(segment, 0L, i, (int)i);
            assertEquals(i, (int)handle.get(segment, 0L, i));
        };

        ArrayChecker LONG = (handle, segment, i) -> {
            handle.set(segment, 0L, i, (long)i);
            assertEquals(i, (long)handle.get(segment, 0L, i));
        };

        ArrayChecker FLOAT = (handle, segment, i) -> {
            handle.set(segment, 0L, i, (float)i);
            assertEquals((float)i, (float)handle.get(segment, 0L, i));
        };

        ArrayChecker DOUBLE = (handle, segment, i) -> {
            handle.set(segment, 0L, i, (double)i);
            assertEquals((double)i, (double)handle.get(segment, 0L, i));
        };
    }

    @DataProvider(name = "matrixElements")
    public Object[][] createMatrixData() {
        return new Object[][] {
                //BE, RW
                { ID, ValueLayout.JAVA_BYTE, MatrixChecker.BYTE },
                { ID, ValueLayout.JAVA_BOOLEAN, MatrixChecker.BOOLEAN },
                { ID, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.SHORT },
                { ID, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.CHAR },
                { ID, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.INT },
                { ID, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.LONG },
                { ID, ValueLayout.ADDRESS.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.ADDR },
                { ID, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.FLOAT },
                { ID, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.DOUBLE },
                //BE, RO
                { IMMUTABLE, ValueLayout.JAVA_BYTE, MatrixChecker.BYTE },
                { IMMUTABLE, ValueLayout.JAVA_BOOLEAN, MatrixChecker.BOOLEAN },
                { IMMUTABLE, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.SHORT },
                { IMMUTABLE, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.CHAR },
                { IMMUTABLE, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.INT },
                { IMMUTABLE, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.LONG },
                { IMMUTABLE, ValueLayout.ADDRESS.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.ADDR },
                { IMMUTABLE, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.FLOAT },
                { IMMUTABLE, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN), MatrixChecker.DOUBLE },
                //LE, RW
                { ID, ValueLayout.JAVA_BYTE, MatrixChecker.BYTE },
                { ID, ValueLayout.JAVA_BOOLEAN, MatrixChecker.BOOLEAN },
                { ID, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.SHORT },
                { ID, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.CHAR },
                { ID, ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.INT },
                { ID, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.LONG },
                { ID, ValueLayout.ADDRESS.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.ADDR },
                { ID, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.FLOAT },
                { ID, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.DOUBLE },
                //LE, RO
                { IMMUTABLE, ValueLayout.JAVA_BYTE, MatrixChecker.BYTE },
                { IMMUTABLE, ValueLayout.JAVA_BOOLEAN, MatrixChecker.BOOLEAN },
                { IMMUTABLE, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.SHORT },
                { IMMUTABLE, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.CHAR },
                { IMMUTABLE, ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.INT },
                { IMMUTABLE, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.LONG },
                { IMMUTABLE, ValueLayout.ADDRESS.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.ADDR },
                { IMMUTABLE, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.FLOAT },
                { IMMUTABLE, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN), MatrixChecker.DOUBLE },
        };
    }

    interface MatrixChecker {
        void check(VarHandle handle, MemorySegment segment, long row, long col);

        MatrixChecker BYTE = (handle, segment, r, c) -> {
            handle.set(segment, 0L, r, c, (byte)(r + c));
            assertEquals(r + c, (byte)handle.get(segment, 0L, r, c));
        };

        MatrixChecker BOOLEAN = (handle, segment, r, c) -> {
            handle.set(segment, 0L, r, c, (r + c) != 0);
            assertEquals((r + c) != 0, (boolean)handle.get(segment, 0L, r, c));
        };

        MatrixChecker SHORT = (handle, segment, r, c) -> {
            handle.set(segment, 0L, r, c, (short)(r + c));
            assertEquals(r + c, (short)handle.get(segment, 0L, r, c));
        };

        MatrixChecker CHAR = (handle, segment, r, c) -> {
            handle.set(segment, 0L, r, c, (char)(r + c));
            assertEquals(r + c, (char)handle.get(segment, 0L, r, c));
        };

        MatrixChecker INT = (handle, segment, r, c) -> {
            handle.set(segment, 0L, r, c, (int)(r + c));
            assertEquals(r + c, (int)handle.get(segment, 0L, r, c));
        };

        MatrixChecker LONG = (handle, segment, r, c) -> {
            handle.set(segment, 0L, r, c, r + c);
            assertEquals(r + c, (long)handle.get(segment, 0L, r, c));
        };

        MatrixChecker ADDR = (handle, segment, r, c) -> {
            handle.set(segment, 0L, r, c, MemorySegment.ofAddress(r + c));
            assertEquals(MemorySegment.ofAddress(r + c), (MemorySegment) handle.get(segment, 0L, r, c));
        };

        MatrixChecker FLOAT = (handle, segment, r, c) -> {
            handle.set(segment, 0L, r, c, (float)(r + c));
            assertEquals((float)(r + c), (float)handle.get(segment, 0L, r, c));
        };

        MatrixChecker DOUBLE = (handle, segment, r, c) -> {
            handle.set(segment, 0L, r, c, (double)(r + c));
            assertEquals((double)(r + c), (double)handle.get(segment, 0L, r, c));
        };
    }
}
