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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class TokenReplaceTest {

    public record TestSpec(String str, Optional<String> expectedStr, Optional<Exception> expectedCtorException,
            Optional<Exception> expectedApplyToException, Map<String, String> tokenWithValues, boolean recursive) {

        public TestSpec {
            Objects.requireNonNull(expectedStr);
            Objects.requireNonNull(expectedCtorException);
            Objects.requireNonNull(expectedApplyToException);
            Objects.requireNonNull(tokenWithValues);
            tokenWithValues.values().forEach(Objects::requireNonNull);

            if (expectedStr.isPresent()) {
                if (!(expectedCtorException.isEmpty() && expectedApplyToException.isEmpty())) {
                    throw new IllegalArgumentException();
                }
            } else if (expectedCtorException.isEmpty() == expectedApplyToException.isEmpty()) {
                throw new IllegalArgumentException();
            }
        }

        static final class Builder {

            Builder str(String v) {
                str = v;
                return this;
            }

            Builder recursive(boolean v) {
                recursive = v;
                return this;
            }

            Builder recursive() {
                return recursive(true);
            }

            Builder expect(String v) {
                expectedStr = v;
                return this;
            }

            Builder expectCtorThrow(String v) {
                expectedCtorException = new IllegalArgumentException(v);
                return this;
            }

            Builder expectApplyToNPE() {
                expectedApplyToException = new NullPointerException();
                return this;
            }

            Builder expectInfiniteRecursion() {
                expectedApplyToException = new IllegalStateException("Infinite recursion");
                return this;
            }

            Builder token(String token, String value) {
                tokenWithValues.put(token, value);
                return this;
            }

            TestSpec create() {
                return new TestSpec(str, expectedStr(), Optional.ofNullable(expectedCtorException),
                        Optional.ofNullable(expectedApplyToException), tokenWithValues, recursive);
            }

            private Optional<String> expectedStr() {
                if (expectedCtorException == null && expectedApplyToException == null) {
                    return Optional.ofNullable(expectedStr).or(() -> Optional.of(str));
                } else {
                    return Optional.empty();
                }
            }

            private boolean recursive;
            private String str;
            private String expectedStr;
            private Exception expectedCtorException;
            private Exception expectedApplyToException;
            private final Map<String, String> tokenWithValues = new HashMap<>();
        }

        void test() {
            final var tokens = tokenWithValues.keySet().toArray(String[]::new);
            expectedStr.ifPresent(expected -> {
                final var tokenReplace = new TokenReplace(tokens);
                final String actual;
                if (recursive) {
                    actual = tokenReplace.recursiveApplyTo(str, tokenWithValues::get);
                } else {
                    actual = tokenReplace.applyTo(str, tokenWithValues::get);
                }
                assertEquals(expected, actual);
            });

            expectedCtorException.ifPresent(expected -> {
                final var ex = assertThrows(expected.getClass(), () -> {
                    new TokenReplace(tokens);
                });
                assertEquals(expected.getMessage(), ex.getMessage());
            });

            expectedApplyToException.ifPresent(expected -> {
                final var tokenReplace = new TokenReplace(tokens);
                final var ex = assertThrows(expected.getClass(), () -> {
                    if (recursive) {
                        tokenReplace.recursiveApplyTo(str, tokenWithValues::get);
                    } else {
                        tokenReplace.applyTo(str, tokenWithValues::get);
                    }
                });
                assertEquals(expected.getMessage(), ex.getMessage());
            });
        }
    }

    @ParameterizedTest
    @MethodSource
    public void test(TestSpec spec) {
        spec.test();
    }

    public static Stream<TestSpec> test() {
        return Stream.of(
                testSpec("foo").token("", "B").expectCtorThrow("Empty token in the list of tokens"),
                testSpec("foo").expectCtorThrow("Empty token list"),
                testSpec("a").expect("a").token("b", "B"),
                testSpec("a").expect("A").token("a", "A"),
                testSpec("aaa").expect("AAA").token("a", "A"),
                testSpec("aaa").recursive().expect("{B}{B}{B}").token("a", "b").token("b", "{B}"),
                testSpec("aaa").token("a", "aa").token("aa", "C").expect("Caa"),
                testSpec("aaa").token("a", "aa").token("aa", "C").expect("CC").recursive(),
                testSpec("aaa").expect("A2A").token("a", "A").token("aa", "A2"),
                testSpec("aaa").token("a", "b").token("b", "c").token("c", "a").expect("bbb"),
                testSpec("aaa").token("a", "b").token("b", "").recursive().expect(""),
                testSpec("aaa").token("a", "").recursive().expect(""),
                testSpec("aaa").token("a", "b").token("b", "c").token("c", "a").expectInfiniteRecursion().recursive(),
                testSpec(null).token("a", "b").expectApplyToNPE(),
                testSpec("abc").expect("abc").token(".", "A"),
                testSpec("abc.").expect("abcD").token(".", "D")
        ).map(TestSpec.Builder::create);
    }

    private static final class CountingSupplier implements Supplier<Object> {

        CountingSupplier(Object value, int expectedCount) {
            this.value = value;
            this.expectedCount = expectedCount;
        }

        @Override
        public Object get() {
            counter++;
            return value;
        }

        public Object value() {
            return value;
        }

        void verifyCount() {
            assertEquals(expectedCount, counter);
        }

        private final Object value;
        private int counter;
        private final int expectedCount;
    }

    @Test
    public void testCombine() {
        final var x = new TokenReplace("a");
        final var y = new TokenReplace("aa");

        final var xy = TokenReplace.combine(x, y);

        assertEquals(xy, new TokenReplace("aa", "a"));
        assertEquals(xy, new TokenReplace("a", "aa"));
    }

    @Test
    public void testCombine2() {
        final var x = new TokenReplace("a");
        final var y = new TokenReplace("a");

        final var xy = TokenReplace.combine(x, y);

        assertEquals(xy, new TokenReplace("a", "a"));
        assertEquals(xy, new TokenReplace("a"));
        assertEquals(xy, x);
        assertEquals(xy, y);
    }

    @Test
    public void testCombine3() {
        final var x = new TokenReplace("a");
        final var y = new TokenReplace("b");

        final var xy = TokenReplace.combine(x, y);

        assertEquals(xy, new TokenReplace("a", "b"));
        assertEquals(xy, new TokenReplace("b", "a"));
    }

    @Test
    public void testEquals() {
        final var x = new TokenReplace("x");
        final var y = new TokenReplace("y");
        final var y2 = new TokenReplace("y");

        assertNotEquals(x, y);
        assertNotEquals(x, null);
        assertNotEquals(null, x);
        assertNotEquals(x, "x");

        assertEquals(y, y2);
        assertEquals(y, y);
    }

    @Test
    public void testCreateCachingTokenValueSupplier() {
        final var neverCalledSupplier = new CountingSupplier("", 0);
        final var calledOnceSupplier = new CountingSupplier("foo", 1);
        final var calledOnceNullSupplier = new CountingSupplier(null, 1);

        final var supplier = TokenReplace.createCachingTokenValueSupplier(Map.of(
                "never", neverCalledSupplier,
                "once", calledOnceSupplier,
                "onceNull", calledOnceNullSupplier
        ));

        for (int i = 0; i != 2; i++) {
            assertEquals(calledOnceSupplier.value(), supplier.apply("once"));

            final var ex = assertThrows(NullPointerException.class, () -> supplier.apply("onceNull"));
            assertEquals("Null value for token [onceNull]", ex.getMessage());
        }

        final var ex = assertThrows(NullPointerException.class, () -> supplier.apply("foo"));
        assertEquals("No token value supplier for token [foo]", ex.getMessage());

        neverCalledSupplier.verifyCount();
        calledOnceSupplier.verifyCount();
        calledOnceNullSupplier.verifyCount();
    }

    private static TestSpec.Builder testSpec(String str) {
        return new TestSpec.Builder().str(str);
    }
}
