/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestAdaptVarHandles
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestAdaptVarHandles
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestAdaptVarHandles
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestAdaptVarHandles
 */

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.List;

import org.junit.jupiter.api.Test;

public class TestAdaptVarHandles {

    static MethodHandle S2I;
    static MethodHandle I2S;
    static MethodHandle CTX_I2S;
    static MethodHandle O2I;
    static MethodHandle I2O;
    static MethodHandle S2L;
    static MethodHandle S2L_EX;
    static MethodHandle S2I_EX;
    static MethodHandle I2S_EX;
    static MethodHandle BASE_ADDR;
    static MethodHandle SUM_OFFSETS;
    static MethodHandle VOID_FILTER;

    static {
        try {
            S2I = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "stringToInt", MethodType.methodType(int.class, String.class));
            I2S = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "intToString", MethodType.methodType(String.class, int.class));
            CTX_I2S = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "ctxIntToString",
                    MethodType.methodType(String.class, String.class, String.class, int.class));
            O2I = MethodHandles.explicitCastArguments(S2I, MethodType.methodType(int.class, Object.class));
            I2O = MethodHandles.explicitCastArguments(I2S, MethodType.methodType(Object.class, int.class));
            S2L = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "stringToLong", MethodType.methodType(long.class, String.class));
            S2L_EX = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "stringToLongException", MethodType.methodType(long.class, String.class));
            BASE_ADDR = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "baseAddress", MethodType.methodType(MemorySegment.class, MemorySegment.class));
            SUM_OFFSETS = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "sumOffsets", MethodType.methodType(long.class, long.class, long.class));
            VOID_FILTER = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "void_filter", MethodType.methodType(void.class, String.class));

            MethodHandle s2i_ex = MethodHandles.throwException(int.class, Throwable.class);
            s2i_ex = MethodHandles.insertArguments(s2i_ex, 0, new Throwable());
            S2I_EX = MethodHandles.dropArguments(s2i_ex, 0, String.class);

            MethodHandle i2s_ex = MethodHandles.throwException(String.class, Throwable.class);
            i2s_ex = MethodHandles.insertArguments(i2s_ex, 0, new Throwable());
            I2S_EX = MethodHandles.dropArguments(i2s_ex, 0, int.class);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError();
        }
    }

    static final VarHandle intHandleIndexed = MethodHandles.insertCoordinates(
            ValueLayout.JAVA_INT.arrayElementVarHandle(), 1, 0L);

    static final VarHandle intHandle = MethodHandles.insertCoordinates(ValueLayout.JAVA_INT.varHandle(), 1, 0L);

    static final VarHandle floatHandle = MethodHandles.insertCoordinates(ValueLayout.JAVA_FLOAT.varHandle(), 1, 0L);

    @Test
    public void testFilterValue() throws Throwable {
        ValueLayout layout = ValueLayout.JAVA_INT;
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(layout);
        VarHandle intHandle = layout.varHandle();
        VarHandle i2SHandle = MethodHandles.filterValue(intHandle, S2I, I2S);
        i2SHandle.set(segment, 0L, "1");
        String oldValue = (String)i2SHandle.getAndAdd(segment, 0L, "42");
        assertEquals("1", oldValue);
        String value = (String)i2SHandle.get(segment, 0L);
        assertEquals("43", value);
        boolean swapped = (boolean)i2SHandle.compareAndSet(segment, 0L, "43", "12");
        assertTrue(swapped);
        oldValue = (String)i2SHandle.compareAndExchange(segment, 0L, "12", "42");
        assertEquals("12", oldValue);
        value = (String)i2SHandle.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(segment, 0L);
        assertEquals("42", value);
    }

    @Test
    public void testFilterValueComposite() throws Throwable {
        ValueLayout layout = ValueLayout.JAVA_INT;
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(layout);
        VarHandle intHandle = layout.varHandle();
        MethodHandle CTX_S2I = MethodHandles.dropArguments(S2I, 0, String.class, String.class);
        VarHandle i2SHandle = MethodHandles.filterValue(intHandle, CTX_S2I, CTX_I2S);
        i2SHandle = MethodHandles.insertCoordinates(i2SHandle, 2, "a", "b");
        i2SHandle.set(segment, 0L, "1");
        String oldValue = (String)i2SHandle.getAndAdd(segment, 0L, "42");
        assertEquals("ab1", oldValue);
        String value = (String)i2SHandle.get(segment, 0L);
        assertEquals("ab43", value);
        boolean swapped = (boolean)i2SHandle.compareAndSet(segment, 0L, "43", "12");
        assertTrue(swapped);
        oldValue = (String)i2SHandle.compareAndExchange(segment, 0L, "12", "42");
        assertEquals("ab12", oldValue);
        value = (String)i2SHandle.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(segment, 0L);
        assertEquals("ab42", value);
    }

    @Test
    public void testFilterValueLoose() throws Throwable {
        ValueLayout layout = ValueLayout.JAVA_INT;
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(layout);
        VarHandle intHandle = layout.varHandle();
        VarHandle i2SHandle = MethodHandles.filterValue(intHandle, O2I, I2O);
        i2SHandle.set(segment, 0L, "1");
        String oldValue = (String)i2SHandle.getAndAdd(segment, 0L, "42");
        assertEquals("1", oldValue);
        String value = (String)i2SHandle.get(segment, 0L);
        assertEquals("43", value);
        boolean swapped = (boolean)i2SHandle.compareAndSet(segment, 0L, "43", "12");
        assertTrue(swapped);
        oldValue = (String)i2SHandle.compareAndExchange(segment, 0L, "12", "42");
        assertEquals("12", oldValue);
        value = (String)(Object)i2SHandle.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(segment, 0L);
        assertEquals("42", value);
    }

    @Test
    public void testBadFilterCarrier() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterValue(floatHandle, S2I, I2S);
        });
    }

    @Test
    public void testBadFilterUnboxArity() {
        VarHandle floatHandle = ValueLayout.JAVA_INT.varHandle();
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterValue(floatHandle, S2I.bindTo(""), I2S);
        });
    }

    @Test
    public void testBadFilterBoxArity() {
        VarHandle intHandle = ValueLayout.JAVA_INT.varHandle();
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterValue(intHandle, S2I, I2S.bindTo(42));
        });
    }

    @Test
    public void testBadFilterBoxPrefixCoordinates() {
        VarHandle intHandle = ValueLayout.JAVA_INT.varHandle();
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterValue(intHandle,
                    MethodHandles.dropArguments(S2I, 1, int.class),
                    MethodHandles.dropArguments(I2S, 1, long.class));
        });
    }

    @Test
    public void testBadFilterBoxException() {
        VarHandle intHandle = ValueLayout.JAVA_INT.varHandle();
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterValue(intHandle, I2S, S2L_EX);
        });
    }

    @Test
    public void testBadFilterUnboxException() {
        VarHandle intHandle = ValueLayout.JAVA_INT.varHandle();
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterValue(intHandle, S2L_EX, I2S);
        });
    }

    @Test
    public void testBadFilterBoxHandleException() {
        VarHandle intHandle = ValueLayout.JAVA_INT.varHandle();
        VarHandle vh = MethodHandles.filterValue(intHandle, S2I, I2S_EX);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ValueLayout.JAVA_INT);
            vh.set(seg, 0L, "42");
            assertThrows(IllegalStateException.class, () -> {
                String x = (String) vh.get(seg, 0L);
            });
        }
    }

    @Test
    public void testBadFilterUnboxHandleException() {
        VarHandle intHandle = ValueLayout.JAVA_INT.varHandle();
        VarHandle vh = MethodHandles.filterValue(intHandle, S2I_EX, I2S);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ValueLayout.JAVA_INT);
            assertThrows(IllegalStateException.class, () -> {
                vh.set(seg, 0L, "42"); // should throw
            });
        }
    }

    @Test
    public void testFilterCoordinates() throws Throwable {
        ValueLayout layout = ValueLayout.JAVA_INT;
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(layout);
        VarHandle intHandle_longIndex = MethodHandles.filterCoordinates(intHandleIndexed, 0, BASE_ADDR, S2L);
        intHandle_longIndex.set(segment, "0", 1);
        int oldValue = (int)intHandle_longIndex.getAndAdd(segment, "0", 42);
        assertEquals(1, oldValue);
        int value = (int)intHandle_longIndex.get(segment, "0");
        assertEquals(43, value);
        boolean swapped = (boolean)intHandle_longIndex.compareAndSet(segment, "0", 43, 12);
        assertTrue(swapped);
        oldValue = (int)intHandle_longIndex.compareAndExchange(segment, "0", 12, 42);
        assertEquals(12, oldValue);
        value = (int)intHandle_longIndex.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(segment, "0");
        assertEquals(42, value);
    }

    @Test
    public void testBadFilterCoordinatesNegativePos() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterCoordinates(intHandle, -1, SUM_OFFSETS);
        });
    }

    @Test
    public void testBadFilterCoordinatesPosTooBig() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterCoordinates(intHandle, 1, SUM_OFFSETS);
        });
    }

    @Test
    public void testBadFilterCoordinatesWrongFilterType() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterCoordinates(intHandleIndexed, 1, S2I);
        });
    }

    @Test
    public void testBadFilterCoordinatesWrongFilterException() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterCoordinates(intHandleIndexed, 1, S2L_EX);
        });
    }

    @Test
    public void testBadFilterCoordinatesTooManyFilters() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.filterCoordinates(intHandleIndexed, 1, S2L, S2L);
        });
    }

    @Test
    public void testInsertCoordinates() throws Throwable {
        ValueLayout layout = ValueLayout.JAVA_INT;
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(layout);
        VarHandle intHandle_longIndex = MethodHandles.insertCoordinates(intHandleIndexed, 0, segment, 0L);
        intHandle_longIndex.set(1);
        int oldValue = (int)intHandle_longIndex.getAndAdd(42);
        assertEquals(1, oldValue);
        int value = (int)intHandle_longIndex.get();
        assertEquals(43, value);
        boolean swapped = (boolean)intHandle_longIndex.compareAndSet(43, 12);
        assertTrue(swapped);
        oldValue = (int)intHandle_longIndex.compareAndExchange(12, 42);
        assertEquals(12, oldValue);
        value = (int)intHandle_longIndex.toMethodHandle(VarHandle.AccessMode.GET).invokeExact();
        assertEquals(42, value);
    }

    @Test
    public void testBadInsertCoordinatesNegativePos() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.insertCoordinates(intHandle, -1, 42);
        });
    }

    @Test
    public void testBadInsertCoordinatesPosTooBig() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.insertCoordinates(intHandle, 1, 42);
        });
    }

    @Test
    public void testBadInsertCoordinatesWrongCoordinateType() {
        assertThrows(ClassCastException.class, () -> {
            MethodHandles.insertCoordinates(intHandleIndexed, 1, "Hello");
        });
    }

    @Test
    public void testBadInsertCoordinatesTooManyValues() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.insertCoordinates(intHandleIndexed, 1, 0L, 0L);
        });
    }

    @Test
    public void testPermuteCoordinates() throws Throwable {
        ValueLayout layout = ValueLayout.JAVA_INT;
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(layout);
        VarHandle intHandle_swap = MethodHandles.permuteCoordinates(intHandleIndexed,
                List.of(long.class, MemorySegment.class), 1, 0);
        intHandle_swap.set(0L, segment, 1);
        int oldValue = (int)intHandle_swap.getAndAdd(0L, segment, 42);
        assertEquals(1, oldValue);
        int value = (int)intHandle_swap.get(0L, segment);
        assertEquals(43, value);
        boolean swapped = (boolean)intHandle_swap.compareAndSet(0L, segment, 43, 12);
        assertTrue(swapped);
        oldValue = (int)intHandle_swap.compareAndExchange(0L, segment, 12, 42);
        assertEquals(12, oldValue);
        value = (int)intHandle_swap.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(0L, segment);
        assertEquals(42, value);
    }

    @Test
    public void testBadPermuteCoordinatesTooManyCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.permuteCoordinates(intHandle, List.of(int.class, int.class), new int[2]);
        });
    }

    @Test
    public void testBadPermuteCoordinatesTooFewCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.permuteCoordinates(intHandle, List.of());
        });
    }

    @Test
    public void testBadPermuteCoordinatesIndexTooBig() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.permuteCoordinates(intHandle, List.of(int.class, int.class), 3);
        });
    }

    @Test
    public void testBadPermuteCoordinatesIndexTooSmall() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.permuteCoordinates(intHandle, List.of(int.class, int.class), -1);
        });
    }

    @Test
    public void testCollectCoordinates() throws Throwable {
        ValueLayout layout = ValueLayout.JAVA_INT;
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(layout);
        VarHandle intHandle_sum = MethodHandles.collectCoordinates(intHandleIndexed, 1, SUM_OFFSETS);
        intHandle_sum.set(segment, -2L, 2L, 1);
        int oldValue = (int)intHandle_sum.getAndAdd(segment, -2L, 2L, 42);
        assertEquals(1, oldValue);
        int value = (int)intHandle_sum.get(segment, -2L, 2L);
        assertEquals(43, value);
        boolean swapped = (boolean)intHandle_sum.compareAndSet(segment, -2L, 2L, 43, 12);
        assertTrue(swapped);
        oldValue = (int)intHandle_sum.compareAndExchange(segment, -2L, 2L, 12, 42);
        assertEquals(12, oldValue);
        value = (int)intHandle_sum.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(segment, -2L, 2L);
        assertEquals(42, value);
    }

    @Test
    public void testCollectCoordinatesVoidFilterType() {
        VarHandle handle = MethodHandles.collectCoordinates(intHandle, 0, VOID_FILTER);
        assertEquals(List.of(String.class, MemorySegment.class), handle.coordinateTypes());
    }

    @Test
    public void testBadCollectCoordinatesNegativePos() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.collectCoordinates(intHandle, -1, SUM_OFFSETS);
        });
    }

    @Test
    public void testBadCollectCoordinatesPosTooBig() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.collectCoordinates(intHandle, 1, SUM_OFFSETS);
        });
    }

    @Test
    public void testBadCollectCoordinatesWrongFilterType() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.collectCoordinates(intHandle, 0, SUM_OFFSETS);
        });
    }

    @Test
    public void testBadCollectCoordinatesWrongFilterException() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.collectCoordinates(intHandle, 0, S2L_EX);
        });
    }

    @Test
    public void testDropCoordinates() throws Throwable {
        ValueLayout layout = ValueLayout.JAVA_INT;
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(layout);
        VarHandle intHandle_dummy = MethodHandles.dropCoordinates(intHandleIndexed, 1, float.class, String.class);
        intHandle_dummy.set(segment, 1f, "hello", 0L, 1);
        int oldValue = (int)intHandle_dummy.getAndAdd(segment, 1f, "hello", 0L, 42);
        assertEquals(1, oldValue);
        int value = (int)intHandle_dummy.get(segment, 1f, "hello", 0L);
        assertEquals(43, value);
        boolean swapped = (boolean)intHandle_dummy.compareAndSet(segment, 1f, "hello", 0L, 43, 12);
        assertTrue(swapped);
        oldValue = (int)intHandle_dummy.compareAndExchange(segment, 1f, "hello", 0L, 12, 42);
        assertEquals(12, oldValue);
        value = (int)intHandle_dummy.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(segment, 1f, "hello", 0L);
        assertEquals(42, value);
    }

    @Test
    public void testBadDropCoordinatesNegativePos() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.dropCoordinates(intHandle, -1);
        });
    }

    @Test
    public void testBadDropCoordinatesPosTooBig() {
        assertThrows(IllegalArgumentException.class, () -> {
            MethodHandles.dropCoordinates(intHandle, 2);
        });
    }

    //helper methods

    static int stringToInt(String s) {
        return Integer.valueOf(s);
    }

    static String intToString(int i) {
        return String.valueOf(i);
    }

    static long stringToLong(String s) {
        return Long.valueOf(s);
    }

    static long stringToLongException(String s) throws Throwable {
        return Long.valueOf(s);
    }

    static MemorySegment baseAddress(MemorySegment segment) {
        return segment;
    }

    static long sumOffsets(long l1, long l2) {
        return l1 + l2;
    }

    static void void_filter(String s) { }

    static String ctxIntToString(String a, String b, int i) {
        return a + b + String.valueOf(i);
    }
}
