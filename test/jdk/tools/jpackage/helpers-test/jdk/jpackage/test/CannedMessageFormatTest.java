/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CannedMessageFormatTest {

    @Test
    void test_ctor() {
        var cmf = CannedMessageFormat.create("Foo {1}{1} Bar {0}", 327, Boolean.TRUE);
        assertTrue(cmf.messageFormat().isPresent());
        assertEquals("Foo truetrue Bar 327", cmf.value());
    }

    @Test
    void test_ctor_no_args() {
        var cmf = CannedMessageFormat.create("Foo");
        assertEquals(Optional.empty(), cmf.messageFormat());
        assertEquals("Foo", cmf.value());
    }

    @Test
    void test_ctor_wrong_number_of_args() {
        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CannedMessageFormat.create("Foo", 7, "", 89);
        });
        assertEquals("Expected 0 arguments for [Foo] string, but given 3", ex.getMessage());

        ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CannedMessageFormat.create("Foo {0}", 7, "", 89);
        });
        assertEquals("Expected 1 arguments for [Foo {0}] string, but given 3", ex.getMessage());

        ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CannedMessageFormat.create("Foo {0}");
        });
        assertEquals("Expected 1 arguments for [Foo {0}] string, but given 0", ex.getMessage());
    }

    @Test
    void test_ctor_invalid_parameters() {
        assertThrowsExactly(NullPointerException.class, () -> {
            CannedMessageFormat.create(null);
        });

        assertThrowsExactly(NullPointerException.class, () -> {
            CannedMessageFormat.create("Foo {0}", (String)null);
        });
    }

    @ParameterizedTest
    @MethodSource
    void test_toPattern(TestSpec test) {
        test.expectedPattern().ifPresentOrElse(expectedPattern -> {
            var pattern = CannedMessageFormat.create(test.format(), test.args().toArray()).toPattern(test.formatArgMapper());
            assertEquals(expectedPattern.toString(), pattern.toString());
        }, () -> {
            assertThrowsExactly(IllegalArgumentException.class, () -> {
                CannedMessageFormat.create(test.format(), test.args().toArray());
            });
        });
    }

    @Test
    void testEscapes() {
        assertEquals("Foo 327327 Bar {1}", CannedMessageFormat.create("Foo {0}{0} Bar '{1}'", 327).value());

        assertEquals("Foo '{0}{0}'", CannedMessageFormat.create("Foo '{0}{0}'").value());
    }

    private static Collection<TestSpec> test_toPattern() {

        var testCases = new ArrayList<TestSpec>();

        testCases.addAll(List.of(
                TestSpec.create("", Pattern.compile(Pattern.quote(""))),
                TestSpec.create("", "foo")
        ));

        for (List<Object> args : List.of(List.<Object>of())) {
            Stream.of(
                    "Stop."
            ).map(formatter -> {
                return new TestSpec(formatter, args, Optional.of(Pattern.compile(Pattern.quote(formatter))));
            }).forEach(testCases::add);
        }

        for (List<Object> args : List.of(List.<Object>of("foo"))) {
            Stream.of(
                    "Stop."
            ).map(formatter -> {
                return new TestSpec(formatter, args, Optional.empty());
            }).forEach(testCases::add);
        }

        testCases.add(TestSpec.create("Hello {1} {0}{1}!", Pattern.compile("\\QHello \\E.*\\Q \\E.*.*\\Q!\\E"), "foo", "bar"));
        testCases.add(TestSpec.create("Hello {1} {0}{0} {0}{0}{0} {0}", Pattern.compile("\\QHello \\E.*\\Q \\E.*\\Q \\E.*\\Q \\E.*"), "foo", "bar"));
        testCases.add(TestSpec.create("{0}{0}", Pattern.compile(".*"), "foo"));

        return testCases;
    }

    record TestSpec(String format, List<Object> args, Optional<Pattern> expectedPattern) {
        TestSpec {
            Objects.requireNonNull(format);
            Objects.requireNonNull(args);
            Objects.requireNonNull(expectedPattern);
        }

        Function<Object, Pattern> formatArgMapper() {
            if (Pattern.compile(Pattern.quote(format)).toString().equals(expectedPattern.orElseThrow().toString())) {
                return UNREACHABLE_FORMAT_ARG_MAPPER;
            } else {
                return DEFAULT_FORMAT_ARG_MAPPER;
            }
        }

        static TestSpec create(String format, Pattern expectedPattern, Object... args) {
            return new TestSpec(format, List.of(args), Optional.of(expectedPattern));
        }

        static TestSpec create(String format, Object... args) {
            return new TestSpec(format, List.of(args), Optional.empty());
        }
    }

    private static final Pattern DEFAULT_FORMAT_ARG_PATTERN = Pattern.compile(".*");

    private static final Function<Object, Pattern> DEFAULT_FORMAT_ARG_MAPPER = _ -> {
        return DEFAULT_FORMAT_ARG_PATTERN;
    };

    private static final Function<Object, Pattern> UNREACHABLE_FORMAT_ARG_MAPPER = _ -> {
        throw new AssertionError();
    };
}
