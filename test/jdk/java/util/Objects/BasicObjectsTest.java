/*
 * Copyright (c) 2009, 2022, 2025 Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6797535 6889858 6891113 8013712 8011800 8014365 8280168 8373661
 * @summary Basic tests for methods in java.util.Objects
 * @run junit BasicObjectsTest
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class BasicObjectsTest {

    @Test
    public void testEquals() {
        Object[] values = {null, "42", 42};
        for(int i = 0; i < values.length; i++)
            for(int j = 0; j < values.length; j++) {
                boolean expected = (i == j);
                Object a = values[i];
                Object b = values[j];
                boolean result = Objects.equals(a, b);
                assertEquals(expected, result, "testEquals");
            }
    }

    @Test
    public void testDeepEquals() {
        Object[] values = {null,
                           null, // Change to values later
                           new byte[]  {(byte)1},
                           new short[] {(short)1},
                           new int[]   {1},
                           new long[]  {1L},
                           new char[]  {(char)1},
                           new float[] {1.0f},
                           new double[]{1.0d},
                           new String[]{"one"}};
        values[1] = values;

        for(int i = 0; i < values.length; i++)
            for(int j = 0; j < values.length; j++) {
                boolean expected = (i == j);
                Object a = values[i];
                Object b = values[j];
                boolean result = Objects.deepEquals(a, b);
                assertEquals(expected, result, "testDeepEquals");
            }
    }

    @Test
    public void testHashCode() {
        assertEquals(0, Objects.hashCode(null), "testHashCode");
        String s = "42";
        assertEquals(s.hashCode(), Objects.hashCode(s), "testHashCode");
    }

    @Test
    public void testHash() {
        Object[] data = new String[]{"perfect", "ham", "THC"};

        assertEquals(0, Objects.hash((Object[])null), "testHash");

        assertEquals(Arrays.hashCode(data),
                     Objects.hash("perfect", "ham", "THC"),
                     "testHash");
    }

    @Test
    public void testToString() {
        assertEquals("null", Objects.toString(null), "testToString");
        String s = "Some string";
        assertEquals(s, Objects.toString(s), "testToString");
    }

    @Test
    public void testToString2() {
        String s = "not the default";
        assertEquals(s, Objects.toString(null, s), "testToString2");
        assertEquals(s, Objects.toString(s, "another string"), "testToString2");
    }

    @Test
    public void testToIdentityString() {
        assertThrows(NullPointerException.class,
            () -> Objects.toIdentityString(null),
            "testToIdentityString");

        Object o = new Object(){};
        assertEquals(o.toString(), Objects.toIdentityString(o), "testToIdentityString");

        Object badToString = new Object() {
                @Override
                public String toString() {
                    fail("toString should not be called");
                    return null;
                }
            };
        assertDoesNotThrow(() -> Objects.toIdentityString(badToString),
            "testToIdentityString");

        Object badHashCode = new Object() {
                @Override
                public int hashCode() {
                    fail("hashCode should not be called");
                    return 0;
                }
            };
        assertDoesNotThrow(() -> Objects.toIdentityString(badHashCode),
            "testToIdentityString");
    }

    @Test
    public void testCompare() {
        String[] values = {"e. e. cummings", "zzz"};
        String[] VALUES = {"E. E. Cummings", "ZZZ"};
        compareTest(null, null, 0);
        for(int i = 0; i < values.length; i++) {
            String a = values[i];
            compareTest(a, a, 0);
            for(int j = 0; j < VALUES.length; j++) {
                int expected = Integer.compare(i, j);
                String b = VALUES[j];
                compareTest(a, b, expected);
            }
        }
    }

    private void compareTest(String a, String b, int expected) {
        int result = Objects.compare(a, b, String.CASE_INSENSITIVE_ORDER);
        assertEquals(Integer.signum(expected), Integer.signum(result), "testCompare");
    }

    @Test
    public void testRequireNonNull() {
        final String RNN_1 = "1-arg requireNonNull";
        final String RNN_2 = "2-arg requireNonNull";
        final String RNN_3 = "Supplier requireNonNull";

        Function<String, String> rnn1 = s -> Objects.requireNonNull(s);
        Function<String, String> rnn2 = s -> Objects.requireNonNull(s, "trousers");
        Function<String, String> rnn3 = s -> Objects.requireNonNull(s, () -> "trousers");

        testRNN_NonNull(rnn1, RNN_1);
        testRNN_NonNull(rnn2, RNN_2);
        testRNN_NonNull(rnn3, RNN_3);

        testRNN_Null(rnn1, RNN_1);
        testRNN_Null(rnn2, RNN_2);
        testRNN_Null(rnn3, RNN_3);
    }

    private void testRNN_NonNull(Function<String, String> testFunc,
                                 String testFuncName) {
        String s = testFunc.apply("pants");
        assertSame("pants", s, testFuncName);
    }

    private void testRNN_Null(Function<String, String> testFunc,
                             String testFuncName) {
        assertThrows(
            NullPointerException.class,
            () -> testFunc.apply(null),
            testFuncName);
    }

    @Test
    public void testIsNull() {
        assertTrue(Objects.isNull(null),
            "isNull(null) should return true");
        assertFalse(Objects.isNull(Objects.class),
            "isNull(Objects.class) should return false");
    }

    @Test
    public void testNonNull() {
        assertFalse(Objects.nonNull(null),
            "nonNull(null) should return false");
        assertTrue(Objects.nonNull(Objects.class),
            "nonNull(Objects.class) should return true");
    }

    @Test
    public void testNonNullOf() {
        String defString = new String("default");
        String nullString = null;
        String nonNullString = "non-null";

        String result = Objects.requireNonNullElse(nullString, defString);
        assertSame(defString, result, "testNonNullOf");

        assertSame(nonNullString, Objects.requireNonNullElse(nonNullString, defString),
            "testNonNullOf");

        assertSame(nonNullString, Objects.requireNonNullElse(nonNullString, null),
            "testNonNullOf");

        assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElse(null, null),
            "testNonNullOf");

        assertSame(defString, Objects.requireNonNullElseGet(nullString, () -> defString),
            "testNonNullOf");

        assertSame(nonNullString, Objects.requireNonNullElseGet(nonNullString, () -> defString),
            "testNonNullOf");

        assertSame(nonNullString, Objects.requireNonNullElseGet(nonNullString, () -> null),
            "testNonNullOf");

        assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElseGet(null, () -> null),
            "testNonNullOf");

        assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElseGet(null, null),
            "testNonNullOf");
    }

    @Test
    public void testRequireNonNullWithNullSupplier() {
        assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNull(null, (Supplier<String>) null),
            "testRequireNonNullWithNullSupplier");
    }

    @Test
    public void testRequireNonNullWithSupplierThrowingException() {
        RuntimeException expectedException = new RuntimeException("Supplier exception");
        RuntimeException actualException = assertThrows(
            RuntimeException.class,
            () -> Objects.requireNonNull(null, () -> {
                throw expectedException;
            }),
            "testRequireNonNullWithSupplierThrowingException");
        assertSame(expectedException, actualException,
            "testRequireNonNullWithSupplierThrowingException");
    }

    @Test
    public void testRequireNonNullElseBothNull() {
        assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElse(null, null),
            "testRequireNonNullElseBothNull");
    }

    @Test
    public void testRequireNonNullElseGetWithNullSupplier() {
        assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElseGet(null, null),
            "testRequireNonNullElseGetWithNullSupplier");
    }

    @Test
    public void testRequireNonNullElseGetWithSupplierReturningNull() {
        assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElseGet(null, () -> null),
            "testRequireNonNullElseGetWithSupplierReturningNull");
    }
}
