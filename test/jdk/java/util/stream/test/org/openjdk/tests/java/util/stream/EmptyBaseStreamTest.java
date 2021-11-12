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
import java.util.Map;
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
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

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
 * method, in which we would call
 * {@link EmptyBaseStreamTest#compare(Class, Supplier, Supplier)}
 * The BaseStreamTest checks the methods in the BaseStream,
 * such as parallel(), unordered(), spliterator(), iterator(),
 * onClose(), etc. Subclasses implement the testAll() method
 * and then call compare() with the type that we are
 *
 * @author Heinz Kabutz heinz@javaspecialists.eu
 */

sealed abstract class EmptyBaseStreamTest permits EmptyDoubleStreamTest,
        EmptyIntStreamTest, EmptyLongStreamTest, EmptyStreamTest {
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
     * @param actualSupplier   creates the actual stream
     * @param expectedSupplier creates the expected stream
     * @throws ReflectiveOperationException
     */
    protected final void compare(Class<?> type,
                                 Supplier<?> actualSupplier,
                                 Supplier<?> expectedSupplier) {
        if (type == null) return;
        for (Class<?> superInterface : type.getInterfaces()) {
            compare(superInterface, actualSupplier, expectedSupplier);
        }
        try {
            Class<?>[] parameterTypes = {type, type};
            for (Method method : getClass().getMethods()) {
                if (method.getName().startsWith("test") &&
                        method.getParameterCount() == 2 &&
                        Arrays.equals(parameterTypes,
                                method.getParameterTypes())) {
                    method.invoke(this, actualSupplier.get(),
                            expectedSupplier.get());
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

    private static final Map<Class<?>, Object> magicMap = Map.of(
            int.class, 42,
            long.class, 42L,
            double.class, 42.0
    );

    private Object createMagic(Class<?> type) {
        return magicMap.getOrDefault(type, magicMap.get(int.class));
    }

    protected final Class<? extends Throwable> getThrowableType(ThrowingRunnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable e) {
            return e.getClass();
        }
    }

    protected final void checkExpectedExceptions(
            BaseStream<?, ?> actual, BaseStream<?, ?> expected,
            String methodName, Class<?>... parameterTypes) {
        try {
            Method method = findMethod(actual, methodName, parameterTypes);
            Object[] parameters = Arrays.stream(parameterTypes)
                    .map(type -> type.isInterface() ? failing(type) : createMagic(type))
                    .toArray();
            invokeWithNullParameters(actual, expected, method, parameters);
            checkExceptionsAreTheSame(actual, expected, method, parameters);
            invokeWithNullParameters(actual, expected, method, parameters);
            checkExceptionsAreTheSame(actual, expected, method, parameters);
            invokeWithNullParameters(actual, expected, method, parameters);
            checkExceptionsAreTheSame(actual, expected, method, parameters);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void checkExceptionsAreTheSame(BaseStream<?, ?> actual, BaseStream<?, ?> expected, Method method, Object[] parameters) {
        var actualExceptionType = getThrowableType(actual, method, parameters);
        var expectedExceptionType = getThrowableType(expected, method, parameters);
        assertSame(actualExceptionType, expectedExceptionType,
                getExpectedExceptionMessage(actualExceptionType, expectedExceptionType, method, parameters));
    }

    private String getExpectedExceptionMessage(Class<?> actualExceptionType, Class<?> expectedExceptionType, Method method, Object[] parameters) {
        return "Got " + (actualExceptionType == null ? "no exception" : actualExceptionType.getName())
                + " from method " + method.getName() +
                (Arrays.stream(parameters).map(o -> o == null ? "null" : "?")
                        .collect(Collectors.joining(", ", "(", ")"))) +
                " but was hoping for " + (expectedExceptionType == null ? "no exception" : expectedExceptionType.getName());
    }

    private void invokeWithNullParameters(BaseStream<?, ?> actual, BaseStream<?, ?> expected,
                                          Method method, Object[] parameters) {
        for (int i = 0; i < parameters.length; i++) {
            if (!(parameters[i] instanceof Number)) {
                Object savedParameter = parameters[i];
                parameters[i] = null;
                var actualThrowable = getThrowableType(actual, method, parameters);
                var expectedThrowable = getThrowableType(expected, method, parameters);
                assertSame(actualThrowable, expectedThrowable,
                        getExpectedExceptionMessage(actualThrowable, expectedThrowable, method, parameters));
                parameters[i] = savedParameter;
            }
        }
    }

    private Class<?> getThrowableType(BaseStream<?, ?> stream, Method method, Object[] parameters) {
        try {
            method.invoke(stream, parameters);
        } catch (InvocationTargetException ite) {
            return ite.getCause().getClass();
        } catch (IllegalAccessException e) {
            return e.getClass();
        }
        return null;
    }

    protected final void compareResults(Object actual, Object expected) {
        assertSame(actual.getClass(), expected.getClass());
        if (actual instanceof List<?> actualList
                && expected instanceof List<?> expectedList) {
            assertEquals(actualList, expectedList);
            assertTrue(actualList.isEmpty());
        } else if (actual.getClass().isArray()
                && expected.getClass().isArray()) {
            assertEquals(Array.getLength(actual), Array.getLength(expected));
        } else if (actual instanceof Optional<?> actualOptional
                && expected instanceof Optional<?> expectedOptional) {
            assertEquals(actualOptional, expectedOptional);
            assertTrue(actualOptional.isEmpty());
        } else if (actual instanceof OptionalInt actualOptional
                && expected instanceof OptionalInt expectedOptional) {
            assertEquals(actualOptional, expectedOptional);
            assertTrue(actualOptional.isEmpty());
        } else if (actual instanceof OptionalLong actualOptional
                && expected instanceof OptionalLong expectedOptional) {
            assertEquals(actualOptional, expectedOptional);
            assertTrue(actualOptional.isEmpty());
        } else if (actual instanceof OptionalDouble actualOptional
                && expected instanceof OptionalDouble expectedOptional) {
            assertEquals(actualOptional, expectedOptional);
            assertTrue(actualOptional.isEmpty());
        } else {
            throw new IllegalArgumentException("Invalid parameter types");
        }
    }

    ///// Methods from BaseStream

    public final void testIterator(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        var actualResult = actual.iterator();
        var expectedResult = expected.iterator();
        assertEquals(actualResult.hasNext(), expectedResult.hasNext());
        assertThrows(NoSuchElementException.class, actualResult::next);
        assertThrows(NoSuchElementException.class, expectedResult::next);
        try {
            actualResult.remove();
            fail();
        } catch (IllegalStateException | UnsupportedOperationException ignore) {
        }
        try {
            expectedResult.remove();
            fail();
        } catch (IllegalStateException | UnsupportedOperationException ignore) {
        }
    }

    public final void testIteratorCannotBeCalledAgain(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        checkExpectedExceptions(actual, expected, "iterator");
    }

    public final void testSequential(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        actual = actual.sequential().parallel().sequential();
        expected = expected.sequential().parallel().sequential();
        var actualResult = actual.isParallel();
        var expectedResult = expected.isParallel();
        assertEquals(actualResult, expectedResult);
        assertFalse(actualResult);
    }

    public final void testSequentialIgnoringExpected(BaseStream<?, ?> actual, BaseStream<?, ?> ignored) {
        assertSame(actual, actual.sequential());
    }

    public final void testParallelAndIsParallel(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // The correct way of invoking parallel is to have it inside
        // a call chain, since we might return a new stream (in our
        // emptyStream() case we do)
        actual = actual.parallel();
        expected = expected.parallel();
        var actualResult = actual.isParallel();
        var expectedResult = expected.isParallel();
        assertEquals(actualResult, expectedResult);
        assertTrue(actualResult);
    }

    public final void testUnordered(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        var actualResult = actual.unordered().spliterator();
        var expectedResult = expected.unordered().spliterator();
        assertEquals(actualResult.hasCharacteristics(Spliterator.ORDERED),
                expectedResult.hasCharacteristics(Spliterator.ORDERED));
        assertFalse(actualResult.hasCharacteristics(Spliterator.ORDERED));
    }

    public final void testUnorderedExceptions(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        checkExpectedExceptions(actual, expected, "unordered");
    }

    public final void testOnCloseAndClose(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        var actualResult = new AtomicBoolean(false);
        var expectedResult = new AtomicBoolean(false);
        actual.onClose(() -> actualResult.set(true)).close();
        expected.onClose(() -> expectedResult.set(true)).close();
        assertEquals(actualResult.get(), expectedResult.get());
        assertTrue(actualResult.get());
    }

    public final void testCloseAndOnClose(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        var actualResult = new AtomicBoolean(false);
        var expectedResult = new AtomicBoolean(false);
        actual.close();
        expected.close();
        assertThrows(IllegalStateException.class, () -> actual.onClose(failing(Runnable.class)));
        assertThrows(IllegalStateException.class, () -> expected.onClose(failing(Runnable.class)));
    }

    public final void testOnCloseExceptions(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        assertThrows(NullPointerException.class, () -> actual.onClose(null));
        assertThrows(NullPointerException.class, () -> expected.onClose(null));
    }

    public final void testOnCloseCannotBeCalledAgain(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        var actualResult = actual.iterator();
        var expectedResult = expected.iterator();
        assertThrows(IllegalStateException.class, () -> actual.onClose(failing(Runnable.class)));
        assertThrows(IllegalStateException.class, () -> expected.onClose(failing(Runnable.class)));
    }

    public final void testSpliterator(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        var actualResult = actual.spliterator();
        var expectedResult = expected.spliterator();

        assertEquals(actualResult.characteristics(), expectedResult.characteristics());
        assertTrue(actualResult.estimateSize() == 0 || actualResult.estimateSize() == Long.MAX_VALUE,
                "actualResult.estimateSize() = " + actualResult.estimateSize());
        assertTrue(expectedResult.estimateSize() == 0 || expectedResult.estimateSize() == Long.MAX_VALUE,
                "expectedResult.estimateSize() = " + expectedResult.estimateSize());

//        assertTrue(actualResult.hasCharacteristics(Spliterator.SIZED));
//        assertTrue(actualResult.hasCharacteristics(Spliterator.SUBSIZED));

//        assertFalse(actualResult.hasCharacteristics(Spliterator.CONCURRENT));
//        assertFalse(actualResult.hasCharacteristics(Spliterator.DISTINCT));
//        assertFalse(actualResult.hasCharacteristics(Spliterator.IMMUTABLE));
//        assertFalse(actualResult.hasCharacteristics(Spliterator.NONNULL));
//        assertFalse(actualResult.hasCharacteristics(Spliterator.ORDERED));
//        assertFalse(actualResult.hasCharacteristics(Spliterator.SORTED));

        assertNull(actualResult.trySplit());
        actualResult.tryAdvance(failing(Consumer.class));
        actualResult.forEachRemaining(failing(Consumer.class));
    }

    public final void testSpliteratorExceptions(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        checkExpectedExceptions(actual, expected, "spliterator");
    }

    private void compareCharacteristics(BaseStream<?, ?> actual, BaseStream<?, ?> expected, String... methodsToCall) {
        try {
            for (String method : methodsToCall) {
                actual = (BaseStream<?, ?>) findMethod(actual, method).invoke(actual);
                expected = (BaseStream<?, ?>) findMethod(expected, method).invoke(expected);
            }
            var actualResult = actual.spliterator().characteristics();
            var expectedResult = expected.spliterator().characteristics();
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

    private Method findMethod(BaseStream<?, ?> stream,
                              String method,
                              Class<?>... parameterTypes) throws NoSuchMethodException {
        if (stream instanceof Stream)
            return Stream.class.getMethod(method, parameterTypes);
        if (stream instanceof IntStream)
            return IntStream.class.getMethod(method, parameterTypes);
        if (stream instanceof LongStream)
            return LongStream.class.getMethod(method, parameterTypes);
        if (stream instanceof DoubleStream)
            return DoubleStream.class.getMethod(method, parameterTypes);
        throw new AssertionError();
    }

    private void compareCharacteristics(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        var actualResult = actual.spliterator().characteristics();
        var expectedResult = expected.spliterator().characteristics();
        assertEquals(actualResult, expectedResult);
    }

    public final void testCharacteristicsDistinct(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // distinct()
        compareCharacteristics(actual, expected, "distinct");
    }

    public final void testCharacteristicsDistinctSorted(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // sorted().unordered().distinct()
        compareCharacteristics(actual, expected, "sorted", "unordered", "distinct");
    }

    public final void testCharacteristicsDistinctOrderedSorted(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // distinct().sorted()
        compareCharacteristics(actual, expected, "distinct", "sorted");
    }

    public final void testCharacteristicsSizedSubsized(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // String.empty()
        compareCharacteristics(actual, expected);
    }

    public final void testCharacteristicsSizedSortedSubsized(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // sorted().unordered()
        compareCharacteristics(actual, expected, "sorted", "unordered");
    }

    public final void testCharacteristicsOrderedSizedSortedSubsized(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // sorted()
        compareCharacteristics(actual, expected, "sorted");
    }

    public final void testCharacteristicsParallelDistinct(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // parallel().distinct()
        compareCharacteristics(actual, expected, "parallel", "distinct");
    }

    public final void testCharacteristicsParallelDistinctSorted(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // parallel().sorted().unordered().distinct()
        compareCharacteristics(actual, expected, "sorted", "unordered", "distinct");
    }

    public final void testCharacteristicsParallelSizedSubsized(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // parallel()
        compareCharacteristics(actual, expected, "parallel");
    }

    public final void testCharacteristicsParallelSizedSortedSubsized(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // parallel().sorted().unordered()
        compareCharacteristics(actual, expected, "sorted", "unordered");
    }

    public final void testCharacteristicsParallelDistinctSizedSortedSubsized(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // parallel().distinct().sorted().unordered()
        compareCharacteristics(actual, expected, "distinct", "sorted", "unordered");
    }

    public final void testCharacteristicsParallelOrderedSizedSortedSubsized(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        // parallel().sorted()
        compareCharacteristics(actual, expected, "parallel", "sorted");
    }

    public final void testCharacteristicsParallelDistinctOrderedSizedSortedSubsized(BaseStream<?, ?> actual, BaseStream<?, ?> expected) {
        //parallel().distinct().sorted()
        compareCharacteristics(actual, expected, "parallel", "distinct", "sorted");
    }
}
