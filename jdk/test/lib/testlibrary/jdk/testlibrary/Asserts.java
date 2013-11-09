/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.testlibrary;

/**
 * Asserts that can be used for verifying assumptions in tests.
 *
 * An assertion will throw a {@link RuntimeException} if the assertion isn't
 * valid.  All the asserts can be imported into a test by using a static
 * import:
 *
 * <pre>
 * {@code
 * import static com.oracle.java.testlibrary.Asserts.*;
 * }
 *
 * Always provide a message describing the assumption if the line number of the
 * failing assertion isn't enough to understand why the assumption failed. For
 * example, if the assertion is in a loop or in a method that is called
 * multiple times, then the line number won't provide enough context to
 * understand the failure.
 * </pre>
 */
public class Asserts {

    /**
     * Shorthand for {@link #assertLessThan(T, T)}.
     *
     * @see #assertLessThan(T, T)
     */
    public static <T extends Comparable<T>> void assertLT(T lhs, T rhs) {
        assertLessThan(lhs, rhs);
    }

    /**
     * Shorthand for {@link #assertLessThan(T, T, String)}.
     *
     * @see #assertLessThan(T, T, String)
     */
    public static <T extends Comparable<T>> void assertLT(T lhs, T rhs, String msg) {
        assertLessThan(lhs, rhs, msg);
    }

    /**
     * Calls {@link #assertLessThan(T, T, String)} with a default message.
     *
     * @see #assertLessThan(T, T, String)
     */
    public static <T extends Comparable<T>> void assertLessThan(T lhs, T rhs) {
        String msg = "Expected that " + format(lhs) + " < " + format(rhs);
        assertLessThan(lhs, rhs, msg);
    }

    /**
     * Asserts that {@code lhs} is less than {@code rhs}.
     *
     * @param lhs The left hand side of the comparison.
     * @param rhs The right hand side of the comparison.
     * @param msg A description of the assumption.
     * @throws RuntimeException if the assertion isn't valid.
     */
    public static <T extends Comparable<T>>void assertLessThan(T lhs, T rhs, String msg) {
        assertTrue(compare(lhs, rhs, msg) < 0, msg);
    }

    /**
     * Shorthand for {@link #assertLessThanOrEqual(T, T)}.
     *
     * @see #assertLessThanOrEqual(T, T)
     */
    public static <T extends Comparable<T>> void assertLTE(T lhs, T rhs) {
        assertLessThanOrEqual(lhs, rhs);
    }

    /**
     * Shorthand for {@link #assertLessThanOrEqual(T, T, String)}.
     *
     * @see #assertLessThanOrEqual(T, T, String)
     */
    public static <T extends Comparable<T>> void assertLTE(T lhs, T rhs, String msg) {
        assertLessThanOrEqual(lhs, rhs, msg);
    }

    /**
     * Calls {@link #assertLessThanOrEqual(T, T, String)} with a default message.
     *
     * @see #assertLessThanOrEqual(T, T, String)
     */
    public static <T extends Comparable<T>> void assertLessThanOrEqual(T lhs, T rhs) {
        String msg = "Expected that " + format(lhs) + " <= " + format(rhs);
        assertLessThanOrEqual(lhs, rhs, msg);
    }

    /**
     * Asserts that {@code lhs} is less than or equal to {@code rhs}.
     *
     * @param lhs The left hand side of the comparison.
     * @param rhs The right hand side of the comparison.
     * @param msg A description of the assumption.
     * @throws RuntimeException if the assertion isn't valid.
     */
    public static <T extends Comparable<T>> void assertLessThanOrEqual(T lhs, T rhs, String msg) {
        assertTrue(compare(lhs, rhs, msg) <= 0, msg);
    }

    /**
     * Shorthand for {@link #assertEquals(T, T)}.
     *
     * @see #assertEquals(T, T)
     */
    public static void assertEQ(Object lhs, Object rhs) {
        assertEquals(lhs, rhs);
    }

    /**
     * Shorthand for {@link #assertEquals(T, T, String)}.
     *
     * @see #assertEquals(T, T, String)
     */
    public static void assertEQ(Object lhs, Object rhs, String msg) {
        assertEquals(lhs, rhs, msg);
    }

    /**
     * Calls {@link #assertEquals(T, T, String)} with a default message.
     *
     * @see #assertEquals(T, T, String)
     */
    public static void assertEquals(Object lhs, Object rhs) {
        String msg = "Expected " + format(lhs) + " to equal " + format(rhs);
        assertEquals(lhs, rhs, msg);
    }

    /**
     * Asserts that {@code lhs} is equal to {@code rhs}.
     *
     * @param lhs The left hand side of the comparison.
     * @param rhs The right hand side of the comparison.
     * @param msg A description of the assumption.
     * @throws RuntimeException if the assertion isn't valid.
     */
    public static void assertEquals(Object lhs, Object rhs, String msg) {
        if (lhs == null) {
            if (rhs != null) {
                error(msg);
            }
        } else {
            assertTrue(lhs.equals(rhs), msg);
        }
    }

    /**
     * Shorthand for {@link #assertGreaterThanOrEqual(T, T)}.
     *
     * @see #assertGreaterThanOrEqual(T, T)
     */
    public static <T extends Comparable<T>> void assertGTE(T lhs, T rhs) {
        assertGreaterThanOrEqual(lhs, rhs);
    }

    /**
     * Shorthand for {@link #assertGreaterThanOrEqual(T, T, String)}.
     *
     * @see #assertGreaterThanOrEqual(T, T, String)
     */
    public static <T extends Comparable<T>> void assertGTE(T lhs, T rhs, String msg) {
        assertGreaterThanOrEqual(lhs, rhs, msg);
    }

    /**
     * Calls {@link #assertGreaterThanOrEqual(T, T, String)} with a default message.
     *
     * @see #assertGreaterThanOrEqual(T, T, String)
     */
    public static <T extends Comparable<T>> void assertGreaterThanOrEqual(T lhs, T rhs) {
        String msg = "Expected that " + format(lhs) + " >= " + format(rhs);
        assertGreaterThanOrEqual(lhs, rhs, msg);
    }

    /**
     * Asserts that {@code lhs} is greater than or equal to {@code rhs}.
     *
     * @param lhs The left hand side of the comparison.
     * @param rhs The right hand side of the comparison.
     * @param msg A description of the assumption.
     * @throws RuntimeException if the assertion isn't valid.
     */
    public static <T extends Comparable<T>> void assertGreaterThanOrEqual(T lhs, T rhs, String msg) {
        assertTrue(compare(lhs, rhs, msg) >= 0, msg);
    }

    /**
     * Shorthand for {@link #assertGreaterThan(T, T)}.
     *
     * @see #assertGreaterThan(T, T)
     */
    public static <T extends Comparable<T>> void assertGT(T lhs, T rhs) {
        assertGreaterThan(lhs, rhs);
    }

    /**
     * Shorthand for {@link #assertGreaterThan(T, T, String)}.
     *
     * @see #assertGreaterThan(T, T, String)
     */
    public static <T extends Comparable<T>> void assertGT(T lhs, T rhs, String msg) {
        assertGreaterThan(lhs, rhs, msg);
    }

    /**
     * Calls {@link #assertGreaterThan(T, T, String)} with a default message.
     *
     * @see #assertGreaterThan(T, T, String)
     */
    public static <T extends Comparable<T>> void assertGreaterThan(T lhs, T rhs) {
        String msg = "Expected that " + format(lhs) + " > " + format(rhs);
        assertGreaterThan(lhs, rhs, msg);
    }

    /**
     * Asserts that {@code lhs} is greater than {@code rhs}.
     *
     * @param lhs The left hand side of the comparison.
     * @param rhs The right hand side of the comparison.
     * @param msg A description of the assumption.
     * @throws RuntimeException if the assertion isn't valid.
     */
    public static <T extends Comparable<T>> void assertGreaterThan(T lhs, T rhs, String msg) {
        assertTrue(compare(lhs, rhs, msg) > 0, msg);
    }

    /**
     * Shorthand for {@link #assertNotEquals(T, T)}.
     *
     * @see #assertNotEquals(T, T)
     */
    public static void assertNE(Object lhs, Object rhs) {
        assertNotEquals(lhs, rhs);
    }

    /**
     * Shorthand for {@link #assertNotEquals(T, T, String)}.
     *
     * @see #assertNotEquals(T, T, String)
     */
    public static void assertNE(Object lhs, Object rhs, String msg) {
        assertNotEquals(lhs, rhs, msg);
    }

    /**
     * Calls {@link #assertNotEquals(T, T, String)} with a default message.
     *
     * @see #assertNotEquals(T, T, String)
     */
    public static void assertNotEquals(Object lhs, Object rhs) {
        String msg = "Expected " + format(lhs) + " to not equal " + format(rhs);
        assertNotEquals(lhs, rhs, msg);
    }

    /**
     * Asserts that {@code lhs} is not equal to {@code rhs}.
     *
     * @param lhs The left hand side of the comparison.
     * @param rhs The right hand side of the comparison.
     * @param msg A description of the assumption.
     * @throws RuntimeException if the assertion isn't valid.
     */
    public static void assertNotEquals(Object lhs, Object rhs, String msg) {
        if (lhs == null) {
            if (rhs == null) {
                error(msg);
            }
        } else {
            assertFalse(lhs.equals(rhs), msg);
        }
    }

    /**
     * Calls {@link #assertNull(Object, String)} with a default message.
     *
     * @see #assertNull(Object, String)
     */
    public static void assertNull(Object o) {
        assertNull(o, "Expected " + format(o) + " to be null");
    }

    /**
     * Asserts that {@code o} is null.
     *
     * @param o The reference assumed to be null.
     * @param msg A description of the assumption.
     * @throws RuntimeException if the assertion isn't valid.
     */
    public static void assertNull(Object o, String msg) {
        assertEquals(o, null, msg);
    }

    /**
     * Calls {@link #assertNotNull(Object, String)} with a default message.
     *
     * @see #assertNotNull(Object, String)
     */
    public static void assertNotNull(Object o) {
        assertNotNull(o, "Expected non null reference");
    }

    /**
     * Asserts that {@code o} is <i>not</i> null.
     *
     * @param o The reference assumed <i>not</i> to be null,
     * @param msg A description of the assumption.
     * @throws RuntimeException if the assertion isn't valid.
     */
    public static void assertNotNull(Object o, String msg) {
        assertNotEquals(o, null, msg);
    }

    /**
     * Calls {@link #assertFalse(boolean, String)} with a default message.
     *
     * @see #assertFalse(boolean, String)
     */
    public static void assertFalse(boolean value) {
        assertFalse(value, "Expected value to be false");
    }

    /**
     * Asserts that {@code value} is {@code false}.
     *
     * @param value The value assumed to be false.
     * @param msg A description of the assumption.
     * @throws RuntimeException if the assertion isn't valid.
     */
    public static void assertFalse(boolean value, String msg) {
        assertTrue(!value, msg);
    }

    /**
     * Calls {@link #assertTrue(boolean, String)} with a default message.
     *
     * @see #assertTrue(boolean, String)
     */
    public static void assertTrue(boolean value) {
        assertTrue(value, "Expected value to be true");
    }

    /**
     * Asserts that {@code value} is {@code true}.
     *
     * @param value The value assumed to be true.
     * @param msg A description of the assumption.
     * @throws RuntimeException if the assertion isn't valid.
     */
    public static void assertTrue(boolean value, String msg) {
        if (!value) {
            error(msg);
        }
    }

    private static <T extends Comparable<T>> int compare(T lhs, T rhs, String msg) {
        assertNotNull(lhs, msg);
        assertNotNull(rhs, msg);
        return lhs.compareTo(rhs);
    }

    private static String format(Object o) {
        return o == null? "null" : o.toString();
    }

    private static void error(String msg) {
        throw new RuntimeException(msg);
    }

}
