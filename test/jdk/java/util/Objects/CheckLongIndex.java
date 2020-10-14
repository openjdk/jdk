/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Objects.checkLOngIndex/jdk.internal.util.Preconditions.checkLongIndex tests
 * @run testng CheckLongIndex
 * @modules java.base/jdk.internal.util
 */

import jdk.internal.util.Preconditions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

import static org.testng.Assert.*;

public class CheckLongIndex {

    static class AssertingOutOfBoundsException extends RuntimeException {
        public AssertingOutOfBoundsException(String message) {
            super(message);
        }
    }

    static BiFunction<String, List<Long>, AssertingOutOfBoundsException> assertingOutOfBounds(
            String message, String expCheckKind, Long... expArgs) {
        return (checkKind, args) -> {
            assertEquals(checkKind, expCheckKind);
            assertEquals(args, List.of(expArgs));
            try {
                args.clear();
                fail("Out of bounds List<Long> argument should be unmodifiable");
            } catch (Exception e)  {
            }
            return new AssertingOutOfBoundsException(message);
        };
    }

    static BiFunction<String, List<Long>, AssertingOutOfBoundsException> assertingOutOfBoundsReturnNull(
            String expCheckKind, Long... expArgs) {
        return (checkKind, args) -> {
            assertEquals(checkKind, expCheckKind);
            assertEquals(args, List.of(expArgs));
            return null;
        };
    }

    static final long[] VALUES = {0, 1, Long.MAX_VALUE - 1, Long.MAX_VALUE, -1, Long.MIN_VALUE + 1, Long.MIN_VALUE};

    @DataProvider
    static Object[][] checkIndexProvider() {
        List<Object[]> l = new ArrayList<>();
        for (long index : VALUES) {
            for (long length : VALUES) {
                boolean withinBounds = index >= 0 &&
                                       length >= 0 &&
                                       index < length;
                l.add(new Object[]{index, length, withinBounds});
            }
        }
        return l.toArray(new Object[0][0]);
    }

    interface X {
        long apply(long a, long b, long c);
    }

    @Test(dataProvider = "checkIndexProvider")
    public void testCheckIndex(long index, long length, boolean withinBounds) {
        String expectedMessage = withinBounds
                                 ? null
                                 : Preconditions.outOfBoundsExceptionFormatterLong(IndexOutOfBoundsException::new).
                apply("checkLongIndex", List.of(index, length)).getMessage();

        BiConsumer<Class<? extends RuntimeException>, LongSupplier> checker = (ec, s) -> {
            try {
                long rIndex = s.getAsLong();
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
                else
                    assertEquals(e.getMessage(), expectedMessage);
            }
        };

        checker.accept(AssertingOutOfBoundsException.class,
                     () -> Preconditions.checkLongIndex(index, length,
                                                        assertingOutOfBounds(expectedMessage, "checkLongIndex", index, length)));
        checker.accept(IndexOutOfBoundsException.class,
                     () -> Preconditions.checkLongIndex(index, length,
                                                        assertingOutOfBoundsReturnNull("checkLongIndex", index, length)));
        checker.accept(IndexOutOfBoundsException.class,
                     () -> Preconditions.checkLongIndex(index, length, null));
        checker.accept(IndexOutOfBoundsException.class,
                     () -> Objects.checkLongIndex(index, length));
        checker.accept(ArrayIndexOutOfBoundsException.class,
                     () -> Preconditions.checkLongIndex(index, length,
                                                        Preconditions.outOfBoundsExceptionFormatterLong(ArrayIndexOutOfBoundsException::new)));
        checker.accept(StringIndexOutOfBoundsException.class,
                     () -> Preconditions.checkLongIndex(index, length,
                                                        Preconditions.outOfBoundsExceptionFormatterLong(StringIndexOutOfBoundsException::new)));
    }


    @DataProvider
    static Object[][] checkFromToIndexProvider() {
        List<Object[]> l = new ArrayList<>();
        for (long fromIndex : VALUES) {
            for (long toIndex : VALUES) {
                for (long length : VALUES) {
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
    public void testCheckFromToIndex(long fromIndex, long toIndex, long length, boolean withinBounds) {
        String expectedMessage = withinBounds
                                 ? null
                                 : Preconditions.outOfBoundsExceptionFormatterLong(IndexOutOfBoundsException::new).
                apply("checkFromToIndex", List.of(fromIndex, toIndex, length)).getMessage();

        BiConsumer<Class<? extends RuntimeException>, LongSupplier> check = (ec, s) -> {
            try {
                long rIndex = s.getAsLong();
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
                else
                    assertEquals(e.getMessage(), expectedMessage);
            }
        };

        check.accept(AssertingOutOfBoundsException.class,
                     () -> Preconditions.checkFromToLongIndex(fromIndex, toIndex, length,
                                                              assertingOutOfBounds(expectedMessage, "checkFromToLongIndex", fromIndex, toIndex, length)));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Preconditions.checkFromToLongIndex(fromIndex, toIndex, length,
                                                              assertingOutOfBoundsReturnNull("checkFromToLongIndex", fromIndex, toIndex, length)));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Preconditions.checkFromToLongIndex(fromIndex, toIndex, length, null));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Objects.checkFromToLongIndex(fromIndex, toIndex, length));
        check.accept(ArrayIndexOutOfBoundsException.class,
                     () -> Preconditions.checkFromToLongIndex(fromIndex, toIndex, length,
                                                              Preconditions.outOfBoundsExceptionFormatterLong(ArrayIndexOutOfBoundsException::new)));
        check.accept(StringIndexOutOfBoundsException.class,
                     () -> Preconditions.checkFromToLongIndex(fromIndex, toIndex, length,
                                                              Preconditions.outOfBoundsExceptionFormatterLong(StringIndexOutOfBoundsException::new)));
    }


    @DataProvider
    static Object[][] checkFromIndexSizeProvider() {
        List<Object[]> l = new ArrayList<>();
        for (long fromIndex : VALUES) {
            for (long size : VALUES) {
                for (long length : VALUES) {
                    long toIndex = fromIndex + size;

                    boolean withinBounds = fromIndex >= 0L &&
                                           size >= 0L &&
                                           length >= 0L &&
                                           fromIndex <= toIndex && // overflow
                                           toIndex <= length;
                    l.add(new Object[]{fromIndex, size, length, withinBounds});
                }
            }
        }
        return l.toArray(new Object[0][0]);
    }

    @Test(dataProvider = "checkFromIndexSizeProvider")
    public void testCheckFromIndexSize(long fromIndex, long size, long length, boolean withinBounds) {
        String expectedMessage = withinBounds
                                 ? null
                                 : Preconditions.outOfBoundsExceptionFormatterLong(IndexOutOfBoundsException::new).
                apply("checkFromLongIndexSize", List.of(fromIndex, size, length)).getMessage();

        BiConsumer<Class<? extends RuntimeException>, LongSupplier> check = (ec, s) -> {
            try {
                long rIndex = s.getAsLong();
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
                else
                    assertEquals(e.getMessage(), expectedMessage);
            }
        };

        check.accept(AssertingOutOfBoundsException.class,
                     () -> Preconditions.checkFromLongIndexSize(fromIndex, size, length,
                                                                assertingOutOfBounds(expectedMessage, "checkFromLongIndexSize", fromIndex, size, length)));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Preconditions.checkFromLongIndexSize(fromIndex, size, length,
                                                                assertingOutOfBoundsReturnNull("checkFromLongIndexSize", fromIndex, size, length)));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Preconditions.checkFromLongIndexSize(fromIndex, size, length, null));
        check.accept(IndexOutOfBoundsException.class,
                     () -> Objects.checkFromLongIndexSize(fromIndex, size, length));
        check.accept(ArrayIndexOutOfBoundsException.class,
                     () -> Preconditions.checkFromLongIndexSize(fromIndex, size, length,
                                                                Preconditions.outOfBoundsExceptionFormatterLong(ArrayIndexOutOfBoundsException::new)));
        check.accept(StringIndexOutOfBoundsException.class,
                     () -> Preconditions.checkFromLongIndexSize(fromIndex, size, length,
                                                                Preconditions.outOfBoundsExceptionFormatterLong(StringIndexOutOfBoundsException::new)));
    }

    @Test
    public void uniqueMessagesForCheckKinds() {
        BiFunction<String, List<Long>, IndexOutOfBoundsException> f =
                Preconditions.outOfBoundsExceptionFormatterLong(IndexOutOfBoundsException::new);

        List<String> messages = new ArrayList<>();
        // Exact arguments
        messages.add(f.apply("checkLongIndex", List.of(-1L, 0L)).getMessage());
        messages.add(f.apply("checkFromToLongIndex", List.of(-1L, 0L, 0L)).getMessage());
        messages.add(f.apply("checkFromLongIndexSize", List.of(-1L, 0L, 0L)).getMessage());
        // Unknown check kind
        messages.add(f.apply("checkUnknown", List.of(-1L, 0L, 0L)).getMessage());
        // Known check kind with more arguments
        messages.add(f.apply("checkLongIndex", List.of(-1L, 0L, 0L)).getMessage());
        messages.add(f.apply("checkFromToLongIndex", List.of(-1L, 0L, 0L, 0L)).getMessage());
        messages.add(f.apply("checkFromLongIndexSize", List.of(-1L, 0L, 0L, 0L)).getMessage());
        // Known check kind with fewer arguments
        messages.add(f.apply("checkLongIndex", List.of(-1L)).getMessage());
        messages.add(f.apply("checkFromToLongIndex", List.of(-1L, 0L)).getMessage());
        messages.add(f.apply("checkFromLongIndexSize", List.of(-1L, 0L)).getMessage());
        // Null arguments
        messages.add(f.apply(null, null).getMessage());
        messages.add(f.apply("checkNullArguments", null).getMessage());
        messages.add(f.apply(null, List.of(-1L)).getMessage());

        assertEquals(messages.size(), messages.stream().distinct().count());
    }
}
