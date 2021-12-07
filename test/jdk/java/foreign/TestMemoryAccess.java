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
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayout.PathElement;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SequenceLayout;
import jdk.incubator.foreign.ValueLayout;

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
        GroupLayout layout = MemoryLayout.structLayout(MemoryLayout.paddingLayout(elemLayout.bitSize()), elemLayout.withName("elem"));
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
        SequenceLayout seq = MemoryLayout.sequenceLayout(10, MemoryLayout.structLayout(MemoryLayout.paddingLayout(elemLayout.bitSize()), elemLayout.withName("elem")));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(MemoryLayout.PathElement.sequenceElement(), MemoryLayout.PathElement.groupElement("elem")), checker);
    }

    @Test(dataProvider = "arrayElements")
    public void testPaddedArrayAccessByIndexSeq(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, ArrayChecker checker) {
        SequenceLayout seq = MemoryLayout.sequenceLayout(10, MemoryLayout.sequenceLayout(2, elemLayout));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(PathElement.sequenceElement(), MemoryLayout.PathElement.sequenceElement(1)), checker);
    }

    private void testAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout layout, VarHandle handle, Checker checker) {
        MemorySegment outer_segment;
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = viewFactory.apply(MemorySegment.allocateNative(layout, scope));
            boolean isRO = segment.isReadOnly();
            try {
                checker.check(handle, segment);
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
                checker.check(handle, segment.asSlice(layout.byteSize()));
                throw new AssertionError(); //not ok, out of bounds
            } catch (IndexOutOfBoundsException ex) {
                //ok, should fail (out of bounds)
            }
            outer_segment = segment; //leak!
        }
        try {
            checker.check(handle, outer_segment);
            throw new AssertionError(); //not ok, scope is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (scope is closed)
        }
    }

    private void testArrayAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, SequenceLayout seq, VarHandle handle, ArrayChecker checker) {
        MemorySegment outer_segment;
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = viewFactory.apply(MemorySegment.allocateNative(seq, scope));
            boolean isRO = segment.isReadOnly();
            try {
                for (int i = 0; i < seq.elementCount().getAsLong(); i++) {
                    checker.check(handle, segment, i);
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
                checker.check(handle, segment, seq.elementCount().getAsLong());
                throw new AssertionError(); //not ok, out of bounds
            } catch (IndexOutOfBoundsException ex) {
                //ok, should fail (out of bounds)
            }
            outer_segment = segment; //leak!
        }
        try {
            checker.check(handle, outer_segment, 0);
            throw new AssertionError(); //not ok, scope is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (scope is closed)
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
                MemoryLayout.sequenceLayout(10, MemoryLayout.structLayout(MemoryLayout.paddingLayout(elemLayout.bitSize()), elemLayout.withName("elem"))));
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
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = viewFactory.apply(MemorySegment.allocateNative(seq, scope));
            boolean isRO = segment.isReadOnly();
            try {
                for (int i = 0; i < seq.elementCount().getAsLong(); i++) {
                    for (int j = 0; j < ((SequenceLayout) seq.elementLayout()).elementCount().getAsLong(); j++) {
                        checker.check(handle, segment, i, j);
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
                checker.check(handle, segment, seq.elementCount().getAsLong(),
                        ((SequenceLayout)seq.elementLayout()).elementCount().getAsLong());
                throw new AssertionError(); //not ok, out of bounds
            } catch (IndexOutOfBoundsException ex) {
                //ok, should fail (out of bounds)
            }
            outer_segment = segment; //leak!
        }
        try {
            checker.check(handle, outer_segment, 0, 0);
            throw new AssertionError(); //not ok, scope is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (scope is closed)
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
            handle.set(segment, (byte)42);
            assertEquals(42, (byte)handle.get(segment));
        };

        Checker SHORT = (handle, segment) -> {
            handle.set(segment, (short)42);
            assertEquals(42, (short)handle.get(segment));
        };

        Checker CHAR = (handle, segment) -> {
            handle.set(segment, (char)42);
            assertEquals(42, (char)handle.get(segment));
        };

        Checker INT = (handle, segment) -> {
            handle.set(segment, 42);
            assertEquals(42, (int)handle.get(segment));
        };

        Checker LONG = (handle, segment) -> {
            handle.set(segment, (long)42);
            assertEquals(42, (long)handle.get(segment));
        };

        Checker FLOAT = (handle, segment) -> {
            handle.set(segment, (float)42);
            assertEquals((float)42, (float)handle.get(segment));
        };

        Checker DOUBLE = (handle, segment) -> {
            handle.set(segment, (double)42);
            assertEquals((double)42, (double)handle.get(segment));
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
            handle.set(segment, i, (byte)i);
            assertEquals(i, (byte)handle.get(segment, i));
        };

        ArrayChecker SHORT = (handle, segment, i) -> {
            handle.set(segment, i, (short)i);
            assertEquals(i, (short)handle.get(segment, i));
        };

        ArrayChecker CHAR = (handle, segment, i) -> {
            handle.set(segment, i, (char)i);
            assertEquals(i, (char)handle.get(segment, i));
        };

        ArrayChecker INT = (handle, segment, i) -> {
            handle.set(segment, i, (int)i);
            assertEquals(i, (int)handle.get(segment, i));
        };

        ArrayChecker LONG = (handle, segment, i) -> {
            handle.set(segment, i, (long)i);
            assertEquals(i, (long)handle.get(segment, i));
        };

        ArrayChecker FLOAT = (handle, segment, i) -> {
            handle.set(segment, i, (float)i);
            assertEquals((float)i, (float)handle.get(segment, i));
        };

        ArrayChecker DOUBLE = (handle, segment, i) -> {
            handle.set(segment, i, (double)i);
            assertEquals((double)i, (double)handle.get(segment, i));
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
            handle.set(segment, r, c, (byte)(r + c));
            assertEquals(r + c, (byte)handle.get(segment, r, c));
        };

        MatrixChecker BOOLEAN = (handle, segment, r, c) -> {
            handle.set(segment, r, c, (r + c) != 0);
            assertEquals((r + c) != 0, (boolean)handle.get(segment, r, c));
        };

        MatrixChecker SHORT = (handle, segment, r, c) -> {
            handle.set(segment, r, c, (short)(r + c));
            assertEquals(r + c, (short)handle.get(segment, r, c));
        };

        MatrixChecker CHAR = (handle, segment, r, c) -> {
            handle.set(segment, r, c, (char)(r + c));
            assertEquals(r + c, (char)handle.get(segment, r, c));
        };

        MatrixChecker INT = (handle, segment, r, c) -> {
            handle.set(segment, r, c, (int)(r + c));
            assertEquals(r + c, (int)handle.get(segment, r, c));
        };

        MatrixChecker LONG = (handle, segment, r, c) -> {
            handle.set(segment, r, c, r + c);
            assertEquals(r + c, (long)handle.get(segment, r, c));
        };

        MatrixChecker ADDR = (handle, segment, r, c) -> {
            handle.set(segment, r, c, MemoryAddress.ofLong(r + c));
            assertEquals(MemoryAddress.ofLong(r + c), (MemoryAddress)handle.get(segment, r, c));
        };

        MatrixChecker FLOAT = (handle, segment, r, c) -> {
            handle.set(segment, r, c, (float)(r + c));
            assertEquals((float)(r + c), (float)handle.get(segment, r, c));
        };

        MatrixChecker DOUBLE = (handle, segment, r, c) -> {
            handle.set(segment, r, c, (double)(r + c));
            assertEquals((double)(r + c), (double)handle.get(segment, r, c));
        };
    }
}
