/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;


public final class JUnitUtils {

    /**
     * Convenience adapter for {@link Assertions#assertArrayEquals(byte[], byte[])},
     * {@link Assertions#assertArrayEquals(int[], int[])},
     * {@link Assertions#assertArrayEquals(Object[], Object[])}, etc. methods.
     *
     * @param expected the expected array to test for equality
     * @param actual   the actual array to test for equality
     */
    public static void assertArrayEquals(Object expected, Object actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
        } else {
            ARRAY_ASSERTERS.getOrDefault(expected.getClass().componentType(), OBJECT_ARRAY_ASSERTER).acceptUnchecked(expected, actual);
        }
    }

    /**
     * Converts the given exception object to a property map.
     * <p>
     * Values returned by public getters are added to the map. Names of getters are
     * the keys in the returned map. The values are property map representations of
     * the objects returned by the getters. Only {@link Throwable#getMessage()} and
     * {@link Throwable#getCause()} getters are picked for the property map by
     * default. If the exception class has additional getters, they will be added to
     * the map. {@code null} is permitted.
     *
     * @param ex the exception to convert into a property map
     * @return the property map view of the given exception object
     */
    public static Map<String, Object> exceptionAsPropertyMap(Exception ex) {
        return EXCEPTION_OM.toMap(ex);
    }

    public static Exception removeExceptionCause(Exception ex) {
        return new ExceptionCauseRemover(ex);
    }


    public static final class ExceptionPattern {

        public ExceptionPattern() {
        }

        public boolean match(Exception ex) {
            Objects.requireNonNull(ex);

            if (expectedType != null && !expectedType.isInstance(ex)) {
                return false;
            }

            if (expectedMessage != null && !expectedMessage.equals(ex.getMessage())) {
                return false;
            }

            if (expectedCauseType != null && !expectedCauseType.isInstance(ex.getCause())) {
                return false;
            }

            return true;
        }

        public ExceptionPattern hasMessage(String v) {
            expectedMessage = v;
            return this;
        }

        public ExceptionPattern isInstanceOf(Class<? extends Exception> v) {
            expectedType = v;
            return this;
        }

        public ExceptionPattern isCauseInstanceOf(Class<? extends Throwable> v) {
            expectedCauseType = v;
            return this;
        }

        public ExceptionPattern hasCause(boolean v) {
            return isCauseInstanceOf(v ? Exception.class : null);
        }

        public ExceptionPattern hasCause() {
            return hasCause(true);
        }

        private String expectedMessage;
        private Class<? extends Exception> expectedType;
        private Class<? extends Throwable> expectedCauseType;
    }


    private static final class ExceptionCauseRemover extends Exception {

        ExceptionCauseRemover(Exception ex) {
            super(ex.getMessage());
            type = ex.getClass();
        }

        public Class<?> getType() {
            return type;
        }

        private final Class<?> type;

        private static final long serialVersionUID = 1L;
    }


    @FunctionalInterface
    private interface ArrayEqualsAsserter<T> {
        void accept(T expected, T actual);

        @SuppressWarnings("unchecked")
        default void acceptUnchecked(Object expected, Object actual) {
            accept((T)expected, (T)actual);
        }
    }


    private static final Map<Class<?>, ArrayEqualsAsserter<?>> ARRAY_ASSERTERS = Map.of(
            boolean.class, (ArrayEqualsAsserter<boolean[]>)Assertions::assertArrayEquals,
            byte.class, (ArrayEqualsAsserter<byte[]>)Assertions::assertArrayEquals,
            char.class, (ArrayEqualsAsserter<char[]>)Assertions::assertArrayEquals,
            double.class, (ArrayEqualsAsserter<double[]>)Assertions::assertArrayEquals,
            float.class, (ArrayEqualsAsserter<float[]>)Assertions::assertArrayEquals,
            int.class, (ArrayEqualsAsserter<int[]>)Assertions::assertArrayEquals,
            long.class, (ArrayEqualsAsserter<long[]>)Assertions::assertArrayEquals,
            short.class, (ArrayEqualsAsserter<short[]>)Assertions::assertArrayEquals
    );

    private static final ArrayEqualsAsserter<Object[]> OBJECT_ARRAY_ASSERTER = Assertions::assertArrayEquals;

    private static final ObjectMapper EXCEPTION_OM = ObjectMapper.standard().create();
}
