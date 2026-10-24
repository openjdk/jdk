/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.internal.util.function.ThrowingSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class ResultTest {

    @Test
    public void test_ctor_with_value() {
        var result = new Result<String>(Optional.of("foo"), List.of());

        assertTrue(result.hasValue());
        assertFalse(result.hasErrors());
        assertEquals(Optional.of("foo"), result.value());
        assertEquals(List.of(), result.errors());
        assertEquals(Optional.empty(), result.firstError());
    }

    @Test
    public void test_ctor_with_errors() {
        var ex = new Exception("Kaput!");
        var result = new Result<String>(Optional.empty(), List.of(ex));

        assertFalse(result.hasValue());
        assertTrue(result.hasErrors());
        assertEquals(Optional.empty(), result.value());
        assertEquals(List.of(ex), result.errors());
        assertEquals(Optional.of(ex), result.firstError());
    }

    @Test
    public void test_ctor_invalid_npe() {
        assertThrowsExactly(NullPointerException.class, () -> {
            new Result<String>(Optional.of("foo"), null);
        });

        assertThrowsExactly(NullPointerException.class, () -> {
            new Result<String>(null, List.of(new Exception()));
        });

        assertThrowsExactly(NullPointerException.class, () -> {
            new Result<String>(null, null);
        });
    }

    @Test
    public void test_ctor_invalid_both_empty() {
        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            new Result<String>(Optional.empty(), List.of());
        });
        assertEquals("'value' and 'errors' cannot both be non-empty or both be empty", ex.getMessage());
    }

    @Test
    public void test_ctor_invalid_both_non_empty() {
        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            new Result<String>(Optional.of("foo"), List.of(new Exception()));
        });
        assertEquals("'value' and 'errors' cannot both be non-empty or both be empty", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_ofValue(boolean valid) {
        if (valid) {
            assertTrue(Result.ofValue("foo").hasValue());
        } else {
            assertThrowsExactly(NullPointerException.class, () -> {
                Result.ofValue(null);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_ofError(boolean valid) {
        if (valid) {
            var err = new Exception("foo");
            var result = Result.ofError(err);
            assertEquals(List.of(err), result.errors());
        } else {
            assertThrowsExactly(NullPointerException.class, () -> {
                Result.ofError(null);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_ofErrors(boolean valid) {
        if (valid) {
            var errors = List.of(new Exception("foo"), new IllegalArgumentException("bar"));
            var result = Result.ofErrors(errors);
            assertSame(errors, result.errors());
        } else {
            assertThrowsExactly(NullPointerException.class, () -> {
                Result.ofErrors(null);
            });

            assertThrowsExactly(NullPointerException.class, () -> {
                var errors = new ArrayList<Exception>();
                errors.add(new Exception());
                errors.add(null);
                Result.ofErrors(errors);
            });
        }
    }

    @Test
    public void test_of() {
        assertEquals("foo", Result.<String>of(() -> {
            return "foo";
        }).orElseThrow());
    }

    @Test
    public void test_of_null_value() {
        assertThrowsExactly(NullPointerException.class, () -> {
            Result.<String>of(() -> {
                return null;
            });
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_of_throws(boolean declaredExceptionType) {

        Exception cause;
        if (declaredExceptionType) {
            cause = new IOException("foo");
        } else {
            cause = new UnsupportedOperationException("bar");
        }

        ThrowingSupplier<String, IOException> supplier = () -> {
            if (declaredExceptionType) {
                throw (IOException)cause;
            } else {
                throw (UnsupportedOperationException)cause;
            }
        };

        if (declaredExceptionType) {
            var result = Result.<String, IOException>of(supplier, IOException.class);
            assertSame(cause, result.firstError().orElseThrow());
            assertEquals(1, result.errors().size());
        } else {
            var ex = assertThrowsExactly(cause.getClass(), () -> {
                Result.<String, IOException>of(supplier, IOException.class);
            });
            assertSame(cause, ex);
        }
    }

    @Test
    public void test_orElseThrow_hasValue() {
        assertEquals("foo", Result.ofValue("foo").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_orElseThrow(boolean uncheckedException) {
        Exception ex;
        Class<? extends Exception> expectedType;
        if (uncheckedException) {
            ex = new RuntimeException("Kaput!");
            expectedType = ex.getClass();
        } else {
            ex = new Exception("Kaput!");
            expectedType = ExceptionBox.class;
        }

        var actual = assertThrowsExactly(expectedType, Result.ofError(ex)::orElseThrow);

        if (uncheckedException) {
            assertSame(ex, actual);
        } else {
            assertSame(ex, actual.getCause());
        }
    }

    @ParameterizedTest
    @MethodSource
    public void test_map_and_flatMap(MapTestSpec spec) {
        spec.run();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_peekValue(boolean hasValue) {
        var pickedValue = Slot.<String>createEmpty();

        Consumer<String> consumer = v -> {
            assertNotNull(v);
            assertTrue(pickedValue.find().isEmpty());
            pickedValue.set(v);
        };

        Result<String> result;
        if (hasValue) {
            result = Result.ofValue("foo");
        } else {
            result = Result.ofError(new Exception("foo"));
        }
        result.peekValue(consumer);

        if (hasValue) {
            assertEquals("foo", pickedValue.get());
        } else {
            assertTrue(pickedValue.find().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_peekErrors(boolean hasValue) {
        var pickedErrors = Slot.<Collection<? extends Exception>>createEmpty();

        Consumer<Collection<? extends Exception>> consumer = v -> {
            assertNotNull(v);
            assertTrue(pickedErrors.find().isEmpty());
            pickedErrors.set(v);
        };

        Result<String> result;
        if (hasValue) {
            result = Result.ofValue("foo");
        } else {
            result = Result.ofErrors(List.of(new Exception("foo"), new IOException("bar")));
        }
        result.peekErrors(consumer);

        if (hasValue) {
            assertTrue(pickedErrors.find().isEmpty());
        } else {
            assertSame(result.errors(), pickedErrors.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_mapErrors(boolean hasValue) {
        Result<String> result;
        if (hasValue) {
            result = Result.ofValue("foo");
        } else {
            result = Result.ofErrors(List.of(new Exception("foo"), new IOException("bar")));
        }

        if (hasValue) {
            var ex = assertThrowsExactly(IllegalStateException.class, result::mapErrors);
            assertEquals("Can not map errors from a result without errors", ex.getMessage());
        } else {
            assertSame(result, result.mapErrors());
        }
    }

    @Test
    public void test_allHaveValues_empty() {
        assertTrue(Result.allHaveValues());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_allHaveValues(boolean expected) {
        if (expected) {
            assertTrue(Result.allHaveValues(Result.ofValue("foo"), Result.ofValue(37)));
        } else {
            assertFalse(Result.allHaveValues(Result.ofValue("foo"), Result.ofError(new Exception())));
        }
    }

    enum MapFunctionType {
        MAP,
        FLAT_MAP,
        ;
    }

    enum MapFunctionOutcome {
        RETURN_NON_NULL,
        RETURN_NULL,
        THROW,
        FLAT_MAP_NEW_ERRORS(MapFunctionType.FLAT_MAP),
        ;

        MapFunctionOutcome(MapFunctionType... supportedTypes) {
            this.supportedTypes = Set.of(supportedTypes);
        }

        MapFunctionOutcome() {
            this(MapFunctionType.values());
        }

        private final Set<MapFunctionType> supportedTypes;
    }

    record MapTestSpec(boolean hasValue, MapFunctionOutcome outcome, MapFunctionType type) {
        MapTestSpec {
            Objects.requireNonNull(outcome);
            Objects.requireNonNull(type);
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<String>();
            tokens.add(outcome.name());
            if (type == MapFunctionType.FLAT_MAP) {
                tokens.add("flatMap");
            }
            if (!hasValue) {
                tokens.add("no-value");
            }
            return String.join(", ", tokens);
        }

        void run() {
            var builder = MapTest.<Integer, String>build();
            if (hasValue) {
                builder.initialValue(100);
            }

            Function<Integer, String> plainMapper;
            switch (outcome) {
                case RETURN_NON_NULL -> {
                    if (hasValue) {
                        builder.expectValue("200");
                    }
                    plainMapper = v -> {
                        return String.valueOf(v * 2);
                    };
                }
                case RETURN_NULL -> {
                    builder.expectExceptionOfType(NullPointerException.class);
                    plainMapper = _ -> {
                        return null;
                    };
                }
                case THROW -> {
                    var cause = new UnsupportedOperationException("Unsupported");
                    builder.expectException(cause);
                    plainMapper = _ -> {
                        throw cause;
                    };
                }
                case FLAT_MAP_NEW_ERRORS -> {
                    if (hasValue) {
                        // Just a stub to make `builder.create()` pass.
                        builder.expectValue("");
                    }
                    var mappedResult = Result.<String>ofError(new UnsupportedOperationException("Whoopsy-daisy"));
                    var test = builder.create().copyWithMappedValue(mappedResult);
                    test.flatMap(v -> {
                        return mappedResult;
                    });
                    return;
                }
                default -> {
                    throw ExceptionBox.reachedUnreachable();
                }
            }

            var test = builder.create();

            switch (type) {
                case MAP -> {
                    test.map(plainMapper);
                }
                case FLAT_MAP -> {
                    test.flatMap(v -> {
                        return Optional.ofNullable(plainMapper.apply(v)).map(Result::ofValue).orElse(null);
                    });
                }
            }
        }
    }

    private static List<MapTestSpec> test_map_and_flatMap() {
        var data = new ArrayList<MapTestSpec>();
        for (var type : MapFunctionType.values()) {
            for (var outcome : MapFunctionOutcome.values()) {
                if (outcome.supportedTypes.contains(type)) {
                    for (var hasValue : List.of(true, false)) {
                        data.add(new MapTestSpec(hasValue, outcome, type));
                    }
                }
            }
        }
        return data;
    }

    private static final class Counter<T, U> implements Function<T, U> {

        Counter(Function<T, U> impl) {
            this.impl = Objects.requireNonNull(impl);
        }

        @Override
        public U apply(T v) {
            counter++;
            return impl.apply(v);
        }

        int count() {
            return counter;
        }

        private int counter;
        private final Function<T, U> impl;
    }

    private record MapTest<T, U>(
            Result<T> initialValue,
            Optional<Result<U>> mappedValue,
            Optional<Exception> expectedException,
            Optional<Class<? extends Exception>> expectedExceptionType) {

        MapTest {
            Objects.requireNonNull(initialValue);
            Objects.requireNonNull(mappedValue);
            Objects.requireNonNull(expectedException);
            Objects.requireNonNull(expectedExceptionType);

            if (expectedExceptionType.isPresent() && mappedValue.isPresent()) {
                // Bad configuration: the mapping operation is expected to throw,
                // but it also expects it to return a value.
                throw new IllegalArgumentException();
            }

            if (expectedExceptionType.isEmpty() && mappedValue.isEmpty()) {
                // Bad configuration: the mapping operation is expected to return normally (not to throw),
                // but it also doesn't expect a mapped value.
                throw new IllegalArgumentException();
            }

            if (initialValue.hasErrors() && mappedValue.map(Result::hasValue).orElse(false)) {
                // Bad configuration: the initial value has errors but they expect a mapped value without errors.
                throw new IllegalArgumentException();
            }

            expectedException.map(Object::getClass).ifPresent(expectedExpectedExceptionType -> {
                var configuredExpectedExceptionType = expectedExceptionType.orElseThrow();
                if (!configuredExpectedExceptionType.equals(expectedExpectedExceptionType)) {
                    throw new IllegalArgumentException(String.format(
                            "expectedException=%s; expectedExceptionType=%s",
                            expectedExpectedExceptionType, configuredExpectedExceptionType));
                }
            });
        }

        MapTest<T, U> copyWithMappedValue(Result<U> v) {
            return new MapTest<>(initialValue, Optional.of(v), expectedException, expectedExceptionType);
        }

        static <T, U> Builder<T, U> build() {
            return new Builder<>();
        }

        void map(Function<T, U> mapper) {
            map(new Counter<>(mapper), initialValue::map);
        }

        void flatMap(Function<T, Result<U>> mapper) {
            map(new Counter<>(mapper), initialValue::flatMap);
        }

        private <V> void map(Counter<T, V> countingMapper, Function<Counter<T, V>, Result<U>> mapper) {

            if (initialValue.hasErrors()) {
                Result<U> mapped = mapper.apply(countingMapper);
                assertTrue(mapped.hasErrors());
                assertEquals(initialValue.errors(), mapped.errors());
            } else {
                expectedExceptionType.ifPresentOrElse(theExpectedExceptionType -> {
                    var ex = assertThrowsExactly(theExpectedExceptionType, () -> {
                        initialValue.map(countingMapper);
                    });

                    expectedException.ifPresent(theExpectedException -> {
                        assertSame(theExpectedException, ex);
                    });
                }, () -> {
                    Result<U> mapped = mapper.apply(countingMapper);
                    assertEquals(mappedValue.orElseThrow(), mapped);
                });
            }

            if (initialValue.hasValue()) {
                assertEquals(1, countingMapper.count());
            } else {
                assertEquals(0, countingMapper.count());
            }
        }

        static final class Builder<T, U> {

            MapTest<T, U> create() {

                var theInitialValue = Optional.ofNullable(initialValue).orElseGet(() -> {
                    return Result.ofError(new Exception("Kaput!"));
                });

                return new MapTest<>(
                        theInitialValue,
                        Optional.ofNullable(expectedValue).map(Result::ofValue).or(() -> {
                            if (expectedExceptionType == null) {
                                return Optional.of(theInitialValue.mapErrors());
                            } else {
                                return Optional.empty();
                            }
                        }),
                        Optional.ofNullable(expectedException),
                        Optional.ofNullable(expectedExceptionType));
            }

            Builder<T, U> initialValue(Result<T> v) {
                initialValue = v;
                return this;
            }

            Builder<T, U> initialValue(T v) {
                return initialValue(Result.ofValue(v));
            }

            Builder<T, U> expectException(Exception v) {
                expectedException = v;
                if (expectedException != null) {
                    expectedExceptionType = expectedException.getClass();
                    expectValue(null);
                } else {
                    expectedExceptionType = null;
                }
                return this;
            }

            Builder<T, U> expectExceptionOfType(Class<? extends Exception> v) {
                expectedException = null;
                expectedExceptionType = v;
                if (v != null) {
                    expectValue(null);
                }
                return this;
            }

            Builder<T, U> expectValue(U v) {
                expectedValue = v;
                if (v != null) {
                    expectException(null);
                }
                return this;
            }

            private Result<T> initialValue;
            private U expectedValue;
            private Exception expectedException;
            private Class<? extends Exception> expectedExceptionType;
        }
    }
}
