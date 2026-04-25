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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JPackageStringBundleTest {

    @Test
    void test_cannedFormattedString() {
        assertFalse(JPackageStringBundle.MAIN.cannedFormattedString("error.version-string-empty").getValue().isBlank());
    }

    @Test
    void test_cannedFormattedString_equals() {
        var a = JPackageStringBundle.MAIN.cannedFormattedString("error.version-string-empty");
        var b = JPackageStringBundle.MAIN.cannedFormattedString("error.version-string-empty");

        assertEquals(a, b);

        a = JPackageStringBundle.MAIN.cannedFormattedString("message.error-header", "foo");
        b = JPackageStringBundle.MAIN.cannedFormattedString("message.error-header", "foo");

        assertEquals(a, b);

        a = JPackageStringBundle.MAIN.cannedFormattedString("message.error-header", "foo");
        b = JPackageStringBundle.MAIN.cannedFormattedString("message.error-header", "bar");

        assertNotEquals(a, b);
    }

    @Test
    void test_cannedFormattedStringAsPattern() {
        var pred = JPackageStringBundle.MAIN.cannedFormattedStringAsPattern("error.version-string-empty", UNREACHABLE_FORMAT_ARG_MAPPER).asMatchPredicate();

        var str = JPackageStringBundle.MAIN.cannedFormattedString("error.version-string-empty").getValue();
        assertTrue(pred.test(str));
        assertFalse(pred.test(str + str));
    }

    @Test
    void test_cannedFormattedStringAsPattern_with_arg() {
        var pred = JPackageStringBundle.MAIN.cannedFormattedStringAsPattern("message.error-header", DEFAULT_FORMAT_ARG_MAPPER, "foo").asMatchPredicate();

        for (var err : List.of("foo", "bar", "", "Unexpected value")) {
            var str = JPackageStringBundle.MAIN.cannedFormattedString("message.error-header", err).getValue();
            assertTrue(pred.test(str));
            assertFalse(pred.test(err));
        }
    }

    @ParameterizedTest
    @MethodSource
    void test_cannedFormattedString_wrong_argument_count(CannedFormattedString cannedStr) {
        assertThrowsExactly(IllegalArgumentException.class, cannedStr::getValue);
    }

    @Test
    void test_cannedFormattedStringAsPattern_wrong_argument_count() {
        assertThrowsExactly(IllegalArgumentException.class, () -> {
            JPackageStringBundle.MAIN.cannedFormattedStringAsPattern("error.version-string-empty", UNREACHABLE_FORMAT_ARG_MAPPER, "foo");
        });

        assertThrowsExactly(IllegalArgumentException.class, () -> {
            JPackageStringBundle.MAIN.cannedFormattedStringAsPattern("message.error-header", UNREACHABLE_FORMAT_ARG_MAPPER);
        });

        assertThrowsExactly(IllegalArgumentException.class, () -> {
            JPackageStringBundle.MAIN.cannedFormattedStringAsPattern("message.error-header", UNREACHABLE_FORMAT_ARG_MAPPER, "foo", "bar");
        });
    }

    @ParameterizedTest
    @MethodSource
    void test_toPattern(TestSpec test) {
        var pattern = JPackageStringBundle.toPattern(new MessageFormat(test.formatter()), test.formatArgMapper(), test.args().toArray());
        assertEquals(test.expectedPattern().toString(), pattern.toString());
    }

    private static Collection<CannedFormattedString> test_cannedFormattedString_wrong_argument_count() {
        return List.of(
                JPackageStringBundle.MAIN.cannedFormattedString("error.version-string-empty", "foo"),
                JPackageStringBundle.MAIN.cannedFormattedString("message.error-header"),
                JPackageStringBundle.MAIN.cannedFormattedString("message.error-header", "foo", "bar")
        );
    }

    private static Collection<TestSpec> test_toPattern() {

        var testCases = new ArrayList<TestSpec>();

        testCases.addAll(List.of(
                TestSpec.create("", Pattern.compile("")),
                TestSpec.create("", Pattern.compile(""), "foo")
        ));

        for (List<Object> args : List.of(List.<Object>of(), List.<Object>of("foo"))) {
            Stream.of(
                    "Stop."
            ).map(formatter -> {
                return new TestSpec(formatter, args, Pattern.compile(Pattern.quote(formatter)));
            }).forEach(testCases::add);
        }

        testCases.add(TestSpec.create("Hello {1} {0}{1}!", Pattern.compile("\\QHello \\E.*\\Q \\E.*.*\\Q!\\E"), "foo", "bar"));
        testCases.add(TestSpec.create("Hello {1} {0}{0} {0}{0}{0} {0}", Pattern.compile("\\QHello \\E.*\\Q \\E.*\\Q \\E.*\\Q \\E.*"), "foo", "bar"));
        testCases.add(TestSpec.create("{0}{0}", Pattern.compile(".*"), "foo"));

        return testCases;
    }

    record TestSpec(String formatter, List<Object> args, Pattern expectedPattern) {
        TestSpec {
            Objects.requireNonNull(formatter);
            Objects.requireNonNull(args);
            Objects.requireNonNull(expectedPattern);
        }

        Function<Object, Pattern> formatArgMapper() {
            if (Pattern.compile(Pattern.quote(formatter)).toString().equals(expectedPattern.toString())) {
                return UNREACHABLE_FORMAT_ARG_MAPPER;
            } else {
                return DEFAULT_FORMAT_ARG_MAPPER;
            }
        }

        static TestSpec create(String formatter, Pattern expectedPattern, Object... args) {
            return new TestSpec(formatter, List.of(args), expectedPattern);
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
