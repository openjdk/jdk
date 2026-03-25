/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @run junit/othervm -XX:-UseArrayFlattening -XX:-UseNullableValueFlattening NullRestrictedArraysTest
 * @run junit/othervm -XX:+UseArrayFlattening -XX:+UseNullableValueFlattening NullRestrictedArraysTest
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Stream;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class NullRestrictedArraysTest {
    interface I {
        int getValue();
    }
    @LooselyConsistentValue
    static value class Value implements I {
        int v;
        Value() {
            this(0);
        }
        Value(int v) {
            this.v = v;
        }
        public int getValue() {
            return v;
        }
    }

    static class T {
        String s;
        Value obj;  // can be null
        @NullRestricted
        Value value;

        T() {
            value = new Value();
            super();
        }
    }

    static Stream<Arguments> checkedField() throws ReflectiveOperationException {
        Value v = new Value();
        return Stream.of(
                Arguments.of(T.class.getDeclaredField("s"), String.class, "", false),
                Arguments.of(T.class.getDeclaredField("obj"), Value.class, null, false),
                Arguments.of(T.class.getDeclaredField("value"), Value.class, v, true)
        );
    }

    /*
     * Test creating null-restricted arrays
     */
    @ParameterizedTest
    @MethodSource("checkedField")
    public void testNullRestrictedArrays(Field field, Class<?> type, Object initValue,
                                      boolean nullRestricted) throws ReflectiveOperationException {
        boolean nr = ValueClass.isNullRestrictedField(field);
        assertEquals(nr, nullRestricted);
        assertTrue(field.getType() == type);
        Object[] array = nullRestricted
                ? ValueClass.newNullRestrictedAtomicArray(type, 4, initValue)
                : (Object[]) Array.newInstance(type, 4);
        assertTrue(ValueClass.isNullRestrictedArray(array) == nullRestricted);
        for (int i=0; i < array.length; i++) {
            array[i] = type.newInstance();
        }
        if (nullRestricted) {
            // NPE thrown if elements in a null-restricted array set to null
            assertThrows(NullPointerException.class, () -> array[0] = null);
        } else {
            array[0] = null;
        }
    }

    /*
     * Test Arrays::copyOf and Arrays::copyOfRange to create null-restricted arrays.
     */
    @Test
    public void testArraysCopyOf() {
        int len = 4;
        Object[] array = (Object[]) Array.newInstance(Value.class, len);
        Object[] nullRestrictedArray = ValueClass.newNullRestrictedNonAtomicArray(Value.class, len, new Value());
        for (int i=0; i < len; i++) {
            array[i] = new Value(i);
            nullRestrictedArray[i] = new Value(i);
        }
        testCopyOf(array, nullRestrictedArray);
        // Cannot extend a null-restricted array without providing a value to fill the new elements
        // testCopyOfRange(array, nullRestrictedArray, 1, len+2);
    };

    private void testCopyOf(Object[] array, Object[] nullRestrictedArray) {
        Object[] newArray1 = Arrays.copyOf(array, array.length);
        Object[] newArray2 = Arrays.copyOf(nullRestrictedArray, nullRestrictedArray.length);

        assertFalse(ValueClass.isNullRestrictedArray(newArray1));
        assertTrue(ValueClass.isNullRestrictedArray(newArray2));

        // elements in a normal array can be null
        for (int i=0; i < array.length; i++) {
            newArray1[i] = null;
        }
        // NPE thrown if elements in a null-restricted array set to null
        assertThrows(NullPointerException.class, () -> newArray2[0] = null);
    }

    private void testCopyOfRange(Object[] array, Object[] nullRestrictedArray, int from, int to) {
        Object[] newArray1 = Arrays.copyOfRange(array, from, to);

        // elements in a normal array can be null
        for (int i=0; i < newArray1.length; i++) {
            newArray1[i] = null;
        }

        // check the new array padded with null if normal array and
        // zero instance if null-restricted array
        for (int i=0; i < newArray1.length; i++) {
            if (from+1 >= array.length) {
                // padded with null
                assertTrue(newArray1[i] == null);
            }
        }

        if (to > array.length) {
            // NullRestricted arrays do not have a value to fill new array elements
            assertThrows(IllegalArgumentException.class, () -> Arrays.copyOfRange(nullRestrictedArray, from, to));
        } else {
            Object[] newArray2 = Arrays.copyOfRange(nullRestrictedArray, from, to);
            System.out.println("newArray2 " + newArray2.length + " " + Arrays.toString(newArray2));
            // NPE thrown if elements in a null-restricted array set to null
            assertThrows(NullPointerException.class, () -> newArray2[0] = null);
        }
    }

    @Test
    public void testVarHandle() {
        int len = 4;
        Object[] array = (Object[]) Array.newInstance(Value.class, len);
        Object[] nullRestrictedArray = ValueClass.newNullRestrictedNonAtomicArray(Value.class, len, new Value());

        // Test var handles
        testVarHandleArray(array, Value[].class, false);
        testVarHandleArray(array, I[].class, false);
        testVarHandleArray(nullRestrictedArray, Value[].class, true);
        testVarHandleArray(nullRestrictedArray, I[].class, true);
    }

    private void testVarHandleArray(Object[] array, Class<?> arrayClass, boolean isNullRestricted) {
        for (int i=0; i < array.length; i++) {
            array[i] = new Value(i);
        }

        VarHandle vh = MethodHandles.arrayElementVarHandle(arrayClass);
        Value value = new Value(0);
        Value value1 =  new Value(1);
        Value value2 =  new Value(2);

        assertTrue(vh.get(array, 0) == value);
        assertTrue(vh.getVolatile(array, 0) == value);
        assertTrue(vh.getOpaque(array, 0) == value);
        assertTrue(vh.getAcquire(array, 0) == value);

        // test set with null values

        if (!isNullRestricted) {
            // if not null-restricted, we expect these set operations to succeed

            vh.set(array, 0, null);
            assertNull(vh.get(array, 0));
            vh.setVolatile(array, 0, null);
            assertNull(vh.get(array, 0));
            vh.setOpaque(array, 0, null);
            assertNull(vh.get(array, 0));
            vh.setRelease(array, 0, null);
            assertNull(vh.get(array, 0));

            assertTrue(vh.compareAndSet(array, 1, value1, null));
            assertNull(vh.get(array, 0));
            vh.set(array, 1, value1);

            assertEquals(vh.compareAndExchange(array, 1, value1, null), value1);
            assertNull(vh.get(array, 0));
            vh.set(array, 1, value1);

            assertEquals(vh.compareAndExchangeAcquire(array, 1, value1, null), value1);
            assertNull(vh.get(array, 0));
            vh.set(array, 1, value1);

            assertEquals(vh.compareAndExchangeRelease(array, 1, value1, null), value1);
            assertNull(vh.get(array, 0));
            vh.set(array, 1, value1);

            assertTrue(vh.weakCompareAndSet(array, 1, value1, null));
            assertNull(vh.get(array, 0));
            vh.set(array, 1, value1);

            assertTrue(vh.weakCompareAndSetAcquire(array, 1, value1, null));
            assertNull(vh.get(array, 0));
            vh.set(array, 1, value1);

            assertTrue(vh.weakCompareAndSetPlain(array, 1, value1, null));
            assertNull(vh.get(array, 0));
            vh.set(array, 1, value1);

            assertTrue(vh.weakCompareAndSetRelease(array, 1, value1, null));
            assertNull(vh.get(array, 0));
            vh.set(array, 1, value1);
        } else {
            // if null-restricted, we expect these set operations to fail

            assertThrows(NullPointerException.class, () -> vh.set(array, 0, null));
            assertThrows(NullPointerException.class, () -> vh.setVolatile(array, 0, null));
            assertThrows(NullPointerException.class, () -> vh.setOpaque(array, 0, null));
            assertThrows(NullPointerException.class, () -> vh.setRelease(array, 0, null));

            assertThrows(NullPointerException.class, () -> vh.compareAndSet(array, 1, value1, null));
            assertThrows(NullPointerException.class, () -> vh.compareAndExchange(array, 1, value1, null));
            assertThrows(NullPointerException.class, () -> vh.compareAndExchangeAcquire(array, 1, value1, null));
            assertThrows(NullPointerException.class, () -> vh.compareAndExchangeRelease(array, 1, value1, null));
            assertThrows(NullPointerException.class, () -> vh.weakCompareAndSet(array, 1, value1, null));
            assertThrows(NullPointerException.class, () -> vh.weakCompareAndSetAcquire(array, 1, value1, null));
            assertThrows(NullPointerException.class, () -> vh.weakCompareAndSetPlain(array, 1, value1, null));
            assertThrows(NullPointerException.class, () -> vh.weakCompareAndSetRelease(array, 1, value1, null));
        }

        // test set with non-null values

        vh.set(array, 0, value1);
        assertEquals(vh.get(array, 0), value1);
        vh.setVolatile(array, 0, value1);
        assertEquals(vh.get(array, 0), value1);
        vh.setOpaque(array, 0, value1);
        assertEquals(vh.get(array, 0), value1);
        vh.setRelease(array, 0, value1);
        assertEquals(vh.get(array, 0), value1);

        assertTrue(vh.compareAndSet(array, 1, value1, value2));
        assertEquals(vh.get(array, 1), value2);
        vh.set(array, 1, value1);

        assertEquals(vh.compareAndExchange(array, 1, value1, value2), value1);
        assertEquals(vh.get(array, 1), value2);
        vh.set(array, 1, value1);

        assertEquals(vh.compareAndExchangeAcquire(array, 1, value1, value2), value1);
        assertEquals(vh.get(array, 1), value2);
        vh.set(array, 1, value1);

        assertEquals(vh.compareAndExchangeRelease(array, 1, value1, value2), value1);
        assertEquals(vh.get(array, 1), value2);
        vh.set(array, 1, value1);

        assertTrue(vh.weakCompareAndSet(array, 1, value1, value2));
        assertEquals(vh.get(array, 1), value2);
        vh.set(array, 1, value1);

        assertTrue(vh.weakCompareAndSetAcquire(array, 1, value1, value2));
        assertEquals(vh.get(array, 1), value2);
        vh.set(array, 1, value1);

        assertTrue(vh.weakCompareAndSetPlain(array, 1, value1, value2));
        assertEquals(vh.get(array, 1), value2);
        vh.set(array, 1, value1);

        assertTrue(vh.weakCompareAndSetRelease(array, 1, value1, value2));
        assertEquals(vh.get(array, 1), value2);
        vh.set(array, 1, value1);

        // test atomic set with null witness

        assertFalse(vh.compareAndSet(array, 2, null, value1));
        assertEquals(vh.get(array, 2), value2);

        assertNotNull(vh.compareAndExchange(array, 2, null, value1));
        assertEquals(vh.get(array, 2), value2);

        assertNotNull(vh.compareAndExchangeAcquire(array, 2, null, value1));
        assertEquals(vh.get(array, 2), value2);

        assertNotNull(vh.compareAndExchangeRelease(array, 2, null, value1));
        assertEquals(vh.get(array, 2), value2);

        assertFalse(vh.weakCompareAndSet(array, 2, null, value1));
        assertEquals(vh.get(array, 2), value2);

        assertFalse(vh.weakCompareAndSetAcquire(array, 2, null, value1));
        assertEquals(vh.get(array, 2), value2);

        assertFalse(vh.weakCompareAndSetPlain(array, 2, null, value1));
        assertEquals(vh.get(array, 2), value2);

        assertFalse(vh.weakCompareAndSetRelease(array, 2, null, value1));
        assertEquals(vh.get(array, 2), value2);
    }
}
