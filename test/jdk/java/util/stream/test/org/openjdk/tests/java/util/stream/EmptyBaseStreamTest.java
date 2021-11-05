/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.tests.java.util.stream;

import org.testng.annotations.Test;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.BaseStream;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.testng.Assert.*;

/**
 * The new EmptyStream, EmptyIntStream, etc. are supposed to
 * be drop-in replacements for the previous Stream.empty(),
 * IntStream.empty(), etc. factory methods, which used to be
 * generated using {@link
 * StreamSupport#stream(Spliterator, boolean)}. In our tests,
 * we thus compare all the behavior of the empty streams, to
 * ensure that they are exactly the same as before.
 * <p>
 * Implementations of this class have to implement the testAll()
 * method, in which we would call {@link BaseStreamTest#compare(Class, Supplier, Supplier)}
 * The BaseStreamTest checks the methods in the BaseStream,
 * such as parallel(), unordered(), spliterator(), iterator(),
 * onClose(), etc. Subclasses implement the testAll() method
 * and then call compare() with the type that we are
 *
 * @author Heinz Kabutz heinz@javaspecialists.eu
 */
sealed abstract class EmptyBaseStreamTest permits
        EmptyStreamTest, EmptyIntStreamTest,
        EmptyLongStreamTest, EmptyDoubleStreamTest {
    @Test
    public abstract void testAll();

    protected final <T> T failing(Class<T> clazz) {
        return clazz.cast(
                Proxy.newProxyInstance(
                        clazz.getClassLoader(),
                        new Class<?>[]{clazz},
                        (proxy, method, args) -> {
                            throw new AssertionError();
                        }));
    }

    /**
     * Helper function that uses reflection to call the rest of the
     * test method, depending on the parameter types. For example,
     * to test the Stream class, we would search for methods
     * beginning with "test" and with two Stream.class parameters,
     * such as {@link testFilter(Stream,Stream)}.
     *
     * @param type             the type of parameters to find test
     *                         methods for
     * @param expectedSupplier creates the expected stream
     * @param actualSupplier   creates the actual stream
     * @throws ReflectiveOperationException
     */
    protected final <E> void compare(Class<?> type,
                                 Supplier<?> expectedSupplier,
                                 Supplier<?> actualSupplier) {
        if (type == null) return;
        for (Class<?> superInterface : type.getInterfaces()) {
            compare(superInterface, expectedSupplier, actualSupplier);
        }
        try {
            Class<?>[] parameterTypes = {type, type};
            for (Method method : getClass().getMethods()) {
                if (method.getName().startsWith("test") &&
                        method.getParameterCount() == 2 &&
                        Arrays.equals(parameterTypes,
                                method.getParameterTypes())) {
                    method.invoke(this, expectedSupplier.get(),
                            actualSupplier.get());
                }
            }
        } catch (InvocationTargetException e) {
            try {
                throw e.getCause();
            } catch (RuntimeException | Error cause) {
                throw cause;
            } catch (Throwable other) {
                throw new AssertionError(other);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    protected final void compareResults(Object expected, Object actual) {
        assertSame(expected.getClass(), actual.getClass());
        if (expected instanceof List<?> expectedList
                && actual instanceof List<?> actualList) {
            assertEquals(expectedList, actualList);
            assertTrue(actualList.isEmpty());
        } else if (expected.getClass().isArray() && actual.getClass()
                .isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual));
        } else if (expected instanceof Optional<?> expectedOptional
                && actual instanceof Optional<?> actualOptional) {
            assertEquals(expectedOptional, actualOptional);
            assertTrue(actualOptional.isEmpty());
        } else if (expected instanceof OptionalInt expectedOptional
                && actual instanceof OptionalInt actualOptional) {
            assertEquals(expectedOptional, actualOptional);
            assertTrue(actualOptional.isEmpty());
        } else if (expected instanceof OptionalLong expectedOptional
                && actual instanceof OptionalLong actualOptional) {
            assertEquals(expectedOptional, actualOptional);
            assertTrue(actualOptional.isEmpty());
        } else if (expected instanceof OptionalDouble expectedOptional
                && actual instanceof OptionalDouble actualOptional) {
            assertEquals(expectedOptional, actualOptional);
            assertTrue(actualOptional.isEmpty());
        } else {
            throw new IllegalArgumentException("Invalid parameter types");
        }
    }

    ///// Methods from BaseStream

    public final void testIterator(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        var expectedResult = expected.iterator();
        var actualResult = actual.iterator();
        assertEquals(expectedResult.hasNext(), actualResult.hasNext());
        assertThrows(NoSuchElementException.class, expectedResult::next);
        assertThrows(NoSuchElementException.class, actualResult::next);
        try {
            expectedResult.remove();
            fail();
        } catch (IllegalStateException | UnsupportedOperationException ignore) {
        }
        try {
            actualResult.remove();
            fail();
        } catch (IllegalStateException | UnsupportedOperationException ignore) {
        }
    }

    public final void testSequential(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        expected = expected.sequential().parallel().sequential();
        actual = actual.sequential().parallel().sequential();
        var expectedResult = expected.isParallel();
        var actualResult = actual.isParallel();
        assertEquals(expectedResult, actualResult);
        assertFalse(actualResult);
    }

    public final void testSequentialIgnoringExpected(BaseStream<?, ?> ignored, BaseStream<?, ?> actual) {
        assertSame(actual, actual.sequential());
    }

    public final void testParallelAndIsParallel(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // The correct way of invoking parallel is to have it inside
        // a call chain, since we might return a new stream (in our
        // emptyStream() case we do)
        expected = expected.parallel();
        actual = actual.parallel();
        var expectedResult = expected.isParallel();
        var actualResult = actual.isParallel();
        assertEquals(expectedResult, actualResult);
        assertTrue(actualResult);
    }

    public final void testUnordered(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        var expectedResult = expected.unordered().spliterator();
        var actualResult = actual.unordered().spliterator();
        assertEquals(expectedResult.hasCharacteristics(Spliterator.ORDERED),
                actualResult.hasCharacteristics(Spliterator.ORDERED));
        assertFalse(actualResult.hasCharacteristics(Spliterator.ORDERED));
    }

    public final void testOnCloseAndClose(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        var expectedResult = new AtomicBoolean(false);
        var actualResult = new AtomicBoolean(false);
        expected.onClose(() -> expectedResult.set(true)).close();
        actual.onClose(() -> actualResult.set(true)).close();
        assertEquals(expectedResult.get(), actualResult.get());
        assertTrue(actualResult.get());
    }

    public final void testOnCloseParameters(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        assertThrows(NullPointerException.class, () -> expected.onClose(null));
        assertThrows(NullPointerException.class, () -> actual.onClose(null));
    }


    public final void testSpliterator(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        var actualResult = actual.spliterator();

        assertTrue(actualResult.hasCharacteristics(Spliterator.SIZED));
        assertTrue(actualResult.hasCharacteristics(Spliterator.SUBSIZED));

        assertFalse(actualResult.hasCharacteristics(Spliterator.CONCURRENT));
        assertFalse(actualResult.hasCharacteristics(Spliterator.DISTINCT));
        assertFalse(actualResult.hasCharacteristics(Spliterator.IMMUTABLE));
        assertFalse(actualResult.hasCharacteristics(Spliterator.NONNULL));
        assertFalse(actualResult.hasCharacteristics(Spliterator.ORDERED));
        assertFalse(actualResult.hasCharacteristics(Spliterator.SORTED));

        assertNull(actualResult.trySplit());
        actualResult.tryAdvance(failing(Consumer.class));
        actualResult.forEachRemaining(failing(Consumer.class));
    }

    private void compareCharacteristics(BaseStream<?, ?> expected, BaseStream<?, ?> actual, String... methodsToCall) {
        try {
            for (String method : methodsToCall) {
                expected = (BaseStream<?, ?>) findMethod(expected, method).invoke(expected);
                actual = (BaseStream<?, ?>) findMethod(actual, method).invoke(actual);
            }
            var actualResult = actual.spliterator().characteristics();
            var expectedResult = expected.spliterator()
                    .characteristics();
            assertEquals(actualResult, expectedResult);
        } catch (InvocationTargetException e) {
            try {
                throw e.getCause();
            } catch (RuntimeException | Error cause) {
                throw cause;
            } catch (Throwable ex) {
                throw new AssertionError(ex);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private Method findMethod(BaseStream<?, ?> stream, String method) throws NoSuchMethodException {
        if (stream instanceof Stream)
            return Stream.class.getMethod(method);
        if (stream instanceof IntStream)
            return IntStream.class.getMethod(method);
        if (stream instanceof LongStream)
            return LongStream.class.getMethod(method);
        if (stream instanceof DoubleStream)
            return DoubleStream.class.getMethod(method);
        throw new AssertionError();
    }

    private void compareCharacteristics(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        var actualResult = actual.spliterator().characteristics();
        var expectedResult = expected.spliterator()
                .characteristics();
        assertEquals(actualResult, expectedResult);
    }

    public final void testCharacteristicsDistinct(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // distinct()
        compareCharacteristics(expected, actual, "distinct");
    }

    public final void testCharacteristicsDistinctSorted(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // sorted().unordered().distinct()
        compareCharacteristics(expected, actual, "sorted", "unordered", "distinct");
    }

    public final void testCharacteristicsDistinctOrderedSorted(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // distinct().sorted()
        compareCharacteristics(expected, actual, "distinct", "sorted");
    }

    public final void testCharacteristicsSizedSubsized(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // String.empty()
        compareCharacteristics(expected, actual);
    }

    public final void testCharacteristicsSizedSortedSubsized(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // sorted().unordered()
        compareCharacteristics(expected, actual, "sorted", "unordered");
    }

    public final void testCharacteristicsOrderedSizedSortedSubsized(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // sorted()
        compareCharacteristics(expected, actual, "sorted");
    }

    public final void testCharacteristicsParallelDistinct(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // parallel().distinct()
        compareCharacteristics(expected, actual, "parallel", "distinct");
    }

    public final void testCharacteristicsParallelDistinctSorted(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // parallel().sorted().unordered().distinct()
        compareCharacteristics(expected, actual, "sorted", "unordered", "distinct");
    }

    public final void testCharacteristicsParallelSizedSubsized(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // parallel()
        compareCharacteristics(expected, actual, "parallel");
    }

    public final void testCharacteristicsParallelSizedSortedSubsized(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // parallel().sorted().unordered()
        compareCharacteristics(expected, actual, "sorted", "unordered");
    }

    public final void testCharacteristicsParallelDistinctSizedSortedSubsized(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // parallel().distinct().sorted().unordered()
        compareCharacteristics(expected, actual, "distinct", "sorted", "unordered");
    }

    public final void testCharacteristicsParallelOrderedSizedSortedSubsized(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        // parallel().sorted()
        compareCharacteristics(expected, actual, "parallel", "sorted");
    }

    public final void testCharacteristicsParallelDistinctOrderedSizedSortedSubsized(BaseStream<?, ?> expected, BaseStream<?, ?> actual) {
        //parallel().distinct().sorted()
        compareCharacteristics(expected, actual, "parallel", "distinct", "sorted");
    }
}
