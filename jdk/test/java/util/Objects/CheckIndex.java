/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary IndexOutOfBoundsException check index tests
 * @run testng CheckIndex
 * @bug 8135248
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;

import static org.testng.Assert.*;

public class CheckIndex {

    static class AssertingOutOfBoundsException extends RuntimeException {
    }

    static BiFunction<Integer, Integer, AssertingOutOfBoundsException> assertingOutOfBounds(
            int expFromIndex, int expToIndexOrSizeOrLength) {
        return (fromIndex, toIndexOrSizeorLength) -> {
            assertEquals(fromIndex, Integer.valueOf(expFromIndex));
            assertEquals(toIndexOrSizeorLength, Integer.valueOf(expToIndexOrSizeOrLength));
            return new AssertingOutOfBoundsException();
        };
    }

    static final int[] VALUES = {0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, -1, Integer.MIN_VALUE + 1, Integer.MIN_VALUE};

    @DataProvider
    static Object[][] checkIndexProvider() {
        List<Object[]> l = new ArrayList<>();
        for (int index : VALUES) {
            for (int length : VALUES) {
                boolean withinBounds = index >= 0 &&
                                       length >= 0 &&
                                       index < length;
                l.add(new Object[]{index, length, withinBounds});
            }
        }
        return l.toArray(new Object[0][0]);
    }

    interface X {
        int apply(int a, int b, int c);
    }

    @Test(dataProvider = "checkIndexProvider")
    public void testCheckIndex(int index, int length, boolean withinBounds) {
        BiConsumer<Class<? extends RuntimeException>, IntSupplier> check = (ec, s) -> {
            try {
                int rIndex = s.getAsInt();
                if (!withinBounds)
                    fail(String.format(
                            "Index %d is out of bounds of [0, %d), but was reported to be within bounds", index, length));
                assertEquals(rIndex, index);
            }
            catch (RuntimeException e) {
                assertTrue(ec.isInstance(e));
                if (withinBounds)
                    fail(String.format(
                            "Index %d is within bounds of [0, %d), but was reported to be out of bounds", index, length));
            }
        };

        check.accept(AssertingOutOfBoundsException.class,
                     () -> Objects.checkIndex(index, length, assertingOutOfBounds(index, length)));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Objects.checkIndex(index, length, null));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Objects.checkIndex(index, length));
    }


    @DataProvider
    static Object[][] checkFromToIndexProvider() {
        List<Object[]> l = new ArrayList<>();
        for (int fromIndex : VALUES) {
            for (int toIndex : VALUES) {
                for (int length : VALUES) {
                    boolean withinBounds = fromIndex >= 0 &&
                                           toIndex >= 0 &&
                                           length >= 0 &&
                                           fromIndex <= toIndex &&
                                           toIndex <= length;
                    l.add(new Object[]{fromIndex, toIndex, length, withinBounds});
                }
            }
        }
        return l.toArray(new Object[0][0]);
    }

    @Test(dataProvider = "checkFromToIndexProvider")
    public void testCheckFromToIndex(int fromIndex, int toIndex, int length, boolean withinBounds) {
        BiConsumer<Class<? extends RuntimeException>, IntSupplier> check = (ec, s) -> {
            try {
                int rIndex = s.getAsInt();
                if (!withinBounds)
                    fail(String.format(
                            "Range [%d, %d) is out of bounds of [0, %d), but was reported to be withing bounds", fromIndex, toIndex, length));
                assertEquals(rIndex, fromIndex);
            }
            catch (RuntimeException e) {
                assertTrue(ec.isInstance(e));
                if (withinBounds)
                    fail(String.format(
                            "Range [%d, %d) is within bounds of [0, %d), but was reported to be out of bounds", fromIndex, toIndex, length));
            }
        };

        check.accept(AssertingOutOfBoundsException.class,
                     () -> Objects.checkFromToIndex(fromIndex, toIndex, length, assertingOutOfBounds(fromIndex, toIndex)));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Objects.checkFromToIndex(fromIndex, toIndex, length, null));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Objects.checkFromToIndex(fromIndex, toIndex, length));
    }


    @DataProvider
    static Object[][] checkFromIndexSizeProvider() {
        List<Object[]> l = new ArrayList<>();
        for (int fromIndex : VALUES) {
            for (int size : VALUES) {
                for (int length : VALUES) {
                    // Explicitly convert to long
                    long lFromIndex = fromIndex;
                    long lSize = size;
                    long lLength = length;
                    // Avoid overflow
                    long lToIndex = lFromIndex + lSize;

                    boolean withinBounds = lFromIndex >= 0L &&
                                           lSize >= 0L &&
                                           lLength >= 0L &&
                                           lFromIndex <= lToIndex &&
                                           lToIndex <= lLength;
                    l.add(new Object[]{fromIndex, size, length, withinBounds});
                }
            }
        }
        return l.toArray(new Object[0][0]);
    }

    @Test(dataProvider = "checkFromIndexSizeProvider")
    public void testCheckFromIndexSize(int fromIndex, int size, int length, boolean withinBounds) {
        BiConsumer<Class<? extends RuntimeException>, IntSupplier> check = (ec, s) -> {
            try {
                int rIndex = s.getAsInt();
                if (!withinBounds)
                    fail(String.format(
                            "Range [%d, %d + %d) is out of bounds of [0, %d), but was reported to be withing bounds", fromIndex, fromIndex, size, length));
                assertEquals(rIndex, fromIndex);
            }
            catch (RuntimeException e) {
                assertTrue(ec.isInstance(e));
                if (withinBounds)
                    fail(String.format(
                            "Range [%d, %d + %d) is within bounds of [0, %d), but was reported to be out of bounds", fromIndex, fromIndex, size, length));
            }
        };

        check.accept(AssertingOutOfBoundsException.class,
                     () -> Objects.checkFromIndexSize(fromIndex, size, length, assertingOutOfBounds(fromIndex, size)));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Objects.checkFromIndexSize(fromIndex, size, length, null));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Objects.checkFromIndexSize(fromIndex, size, length));
    }

    @Test
    public void checkIndexOutOfBoundsExceptionConstructors() {
        BiConsumer<Class<? extends RuntimeException>, IntSupplier> check = (ec, s) -> {
            try {
                s.getAsInt();
                fail("Runtime exception expected");
            }
            catch (RuntimeException e) {
                assertTrue(ec.isInstance(e));
            }
        };

        check.accept(IndexOutOfBoundsException.class,
                     () -> Objects.checkIndex(1, 0, IndexOutOfBoundsException::new));
        check.accept(StringIndexOutOfBoundsException.class,
                     () -> Objects.checkIndex(1, 0, StringIndexOutOfBoundsException::new));
        check.accept(ArrayIndexOutOfBoundsException.class,
                     () -> Objects.checkIndex(1, 0, ArrayIndexOutOfBoundsException::new));
    }
}
