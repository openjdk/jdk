/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
        assertEquals(0, Objects.hashCode(null),
            "hashCode(null) should return 0");
        String s = "42";
        assertEquals(s.hashCode(), Objects.hashCode(s),
            "hashCode(s) should return s.hashCode()");
    }

    @Test
    public void testHash() {
        Object[] data = new String[]{"perfect", "ham", "THC"};

        assertEquals(0, Objects.hash((Object[])null),
            "hash(null) should return 0");

        assertEquals(Arrays.hashCode(data),
                     Objects.hash("perfect", "ham", "THC"),
                     "hash should match Arrays.hashCode");
    }

    @Test
    public void testToString() {
        assertEquals("null", Objects.toString(null),
            "toString(null) should return 'null'");
        String s = "Some string";
        assertEquals(s, Objects.toString(s),
            "toString(s) should return s");
    }

    @Test
    public void testToString2() {
        String s = "not the default";
        assertEquals(s, Objects.toString(null, s),
            "toString(null, default) should return default");
        assertEquals(s, Objects.toString(s, "another string"),
            "toString(s, default) should return s when s is not null");
    }

    @Test
    public void testToIdentityString() {
        // Test null behavior
        assertThrows(NullPointerException.class,
            () -> Objects.toIdentityString(null),
            "toIdentityString(null) should throw NullPointerException");

        // Behavior on typical objects
        Object o = new Object(){};
        assertEquals(o.toString(), Objects.toIdentityString(o),
            "toIdentityString(o) should equal o.toString()");

        // Verify object's toString *not* called
        Object badToString = new Object() {
                @Override
                public String toString() {
                    fail("toString should not be called");
                    return null;
                }
            };
        // Should not throw RuntimeException from toString
        assertDoesNotThrow(() -> Objects.toIdentityString(badToString),
            "toIdentityString should not call toString()");

        // Verify object's hashCode *not* called
        Object badHashCode = new Object() {
                @Override
                public int hashCode() {
                    fail("hashCode should not be called");
                    return 0;
                }
            };
        // Should not throw RuntimeException from hashCode
        assertDoesNotThrow(() -> Objects.toIdentityString(badHashCode),
            "toIdentityString should not call hashCode()");
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

        testRNN_Null(rnn1, RNN_1, null);
        testRNN_Null(rnn2, RNN_2, "trousers");
        testRNN_Null(rnn3, RNN_3, "trousers");
    }

    private void testRNN_NonNull(Function<String, String> testFunc,
                                 String testFuncName) {
        String s = testFunc.apply("pants");
        assertSame("pants", s,
            testFuncName + " should return its arg");
    }

    private void testRNN_Null(Function<String, String> testFunc,
                              String testFuncName,
                              String expectedMessage) {
        NullPointerException npe = assertThrows(
            NullPointerException.class,
            () -> testFunc.apply(null),
            testFuncName);
        assertEquals(expectedMessage, npe.getMessage(),
            testFuncName + " should have correct exception message");
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

        // Confirm the compile time return type matches
        String result = Objects.requireNonNullElse(nullString, defString);
        assertSame(defString, result,
            "requireNonNullElse(null, default) should return default");

        assertSame(nonNullString, Objects.requireNonNullElse(nonNullString, defString),
            "requireNonNullElse(non-null, default) should return non-null");

        assertSame(nonNullString, Objects.requireNonNullElse(nonNullString, null),
            "requireNonNullElse(non-null, null) should return non-null");

        NullPointerException npe1 = assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElse(null, null),
            "requireNonNullElse(null, null) should throw NPE");
        assertEquals("defaultObj", npe1.getMessage(),
            "Exception message should be 'defaultObj'");

        // Test requireNonNullElseGet with a supplier
        assertSame(defString, Objects.requireNonNullElseGet(nullString, () -> defString),
            "requireNonNullElseGet(null, supplier) should return supplier result");

        assertSame(nonNullString, Objects.requireNonNullElseGet(nonNullString, () -> defString),
            "requireNonNullElseGet(non-null, supplier) should return non-null");

        assertSame(nonNullString, Objects.requireNonNullElseGet(nonNullString, () -> null),
            "requireNonNullElseGet(non-null, null-supplier) should return non-null");

        NullPointerException npe2 = assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElseGet(null, () -> null),
            "requireNonNullElseGet(null, null-returning supplier) should throw NPE");
        assertEquals("supplier.get()", npe2.getMessage(),
            "Exception message should be 'supplier.get()'");

        NullPointerException npe3 = assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElseGet(null, null),
            "requireNonNullElseGet(null, null supplier) should throw NPE");
        assertEquals("supplier", npe3.getMessage(),
            "Exception message should be 'supplier'");
    }

    /**
     * Test requireNonNull with null Supplier parameter.
     * Should throw NullPointerException with null message (as per API spec).
     */
    @Test
    public void testRequireNonNullWithNullSupplier() {
        // Test with null object and null supplier
        NullPointerException npe = assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNull(null, (Supplier<String>) null),
            "requireNonNull(null, null supplier) should throw NPE");
        // According to Objects.java implementation, when messageSupplier is null,
        // the exception message is also null
        assertNull(npe.getMessage(),
            "Exception message should be null when messageSupplier is null");
    }

    /**
     * Test requireNonNull with Supplier that throws exception.
     * The exception from supplier should be thrown, not wrapped.
     */
    @Test
    public void testRequireNonNullWithSupplierThrowingException() {
        // Allocate exception outside of lambda to verify same instance is thrown
        RuntimeException expectedException = new RuntimeException("Supplier exception");
        RuntimeException actualException = assertThrows(
            RuntimeException.class,
            () -> Objects.requireNonNull(null, () -> {
                throw expectedException;
            }),
            "requireNonNull should throw exception from supplier");
        assertSame(expectedException, actualException,
            "The exception from supplier should be thrown directly, not wrapped");
    }

    /**
     * Test requireNonNullElse with both arguments null.
     * Should throw NullPointerException with message "defaultObj".
     */
    @Test
    public void testRequireNonNullElseBothNull() {
        NullPointerException npe = assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElse(null, null),
            "requireNonNullElse(null, null) should throw NPE");
        assertEquals("defaultObj", npe.getMessage(),
            "Exception message should be 'defaultObj' when both arguments are null");
    }

    /**
     * Test requireNonNullElseGet with null supplier.
     * Should throw NullPointerException with message "supplier".
     */
    @Test
    public void testRequireNonNullElseGetWithNullSupplier() {
        NullPointerException npe = assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElseGet(null, null),
            "requireNonNullElseGet(null, null supplier) should throw NPE");
        assertEquals("supplier", npe.getMessage(),
            "Exception message should be 'supplier' when supplier is null");
    }

    /**
     * Test requireNonNullElseGet with supplier returning null.
     * Should throw NullPointerException with message "supplier.get()".
     */
    @Test
    public void testRequireNonNullElseGetWithSupplierReturningNull() {
        NullPointerException npe = assertThrows(
            NullPointerException.class,
            () -> Objects.requireNonNullElseGet(null, () -> null),
            "requireNonNullElseGet(null, null-returning supplier) should throw NPE");
        assertEquals("supplier.get()", npe.getMessage(),
            "Exception message should be 'supplier.get()' when supplier returns null");
    }
}
