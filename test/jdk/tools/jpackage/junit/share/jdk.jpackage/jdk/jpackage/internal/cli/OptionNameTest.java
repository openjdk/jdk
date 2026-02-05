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
package jdk.jpackage.internal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class OptionNameTest {

    record TestSpec(String value, String expectedName, String expectedOnCmdLine, boolean expectedIsShort) {
        TestSpec {
            Objects.requireNonNull(value);
            Objects.requireNonNull(expectedName);
            Objects.requireNonNull(expectedOnCmdLine);
        }

        void run() {
            final var optionName = OptionName.of(value);

            assertEquals(expectedName, optionName.name());
            assertEquals(expectedOnCmdLine, optionName.formatForCommandLine());
            assertEquals(expectedIsShort, optionName.isShort());
        }

        static final class Builder {

            TestSpec create() {
                return new TestSpec(value, validatedExpectedName(), validatedExpectedOnCmdLine(), validatedExpectedIsShort());
            }

            Builder value(String v) {
                value = v;
                return this;
            }

            Builder expectedName(String v) {
                expectedName = v;
                return this;
            }

            Builder expectedOnCmdLine(String v) {
                expectedOnCmdLine = v;
                return this;
            }

            Builder isShort() {
                expectedIsShort = true;
                return this;
            }

            private String validatedExpectedName() {
                return Optional.ofNullable(expectedName).orElse(value);
            }

            private String validatedExpectedOnCmdLine() {
                return Optional.ofNullable(expectedOnCmdLine).orElseGet(() -> {
                    if (validatedExpectedIsShort()) {
                        return "-" + validatedExpectedName();
                    } else {
                        return "--" + validatedExpectedName();
                    }
                });
            }

            private boolean validatedExpectedIsShort() {
                return Optional.ofNullable(expectedIsShort).orElseGet(() -> {
                    return validatedExpectedName().length() == 1;
                });
            }

            private String value;
            private String expectedName;
            private String expectedOnCmdLine;
            private Boolean expectedIsShort;
        }
    }

    @ParameterizedTest
    @MethodSource
    public void test(TestSpec testSpec) {
        testSpec.run();
    }

    private static List<TestSpec> test() {
        return Stream.of(
                build("foo"),
                build("x"),
                build("--foo").expectedName("foo"),
                build("--f").expectedName("f").isShort(),
                build("-foo").expectedName("f").isShort(),
                build("-x").expectedName("x").isShort(),
                build("---").expectedName("-")
        ).map(TestSpec.Builder::create).toList();
    }

    @ParameterizedTest
    @MethodSource
    public void negativeTest(Map.Entry<String, String> testSpec) {
        final var ex = assertThrowsExactly(IllegalArgumentException.class, () -> OptionName.of(testSpec.getKey()));
        assertEquals(testSpec.getValue(), ex.getMessage());
    }

    @Test
    public void compareTest() {
        assertTrue(0 == OptionName.of("a").compareTo(OptionName.of("a")));
        assertTrue(OptionName.of("a").compareTo(OptionName.of("b")) < 0);
        assertTrue(OptionName.of("b").compareTo(OptionName.of("a")) > 0);
    }

    private static Collection<Map.Entry<String, String>> negativeTest() {
        return Map.of(
                "", "Name should not be empty",
                "-", "Short option without a name",
                "--", "Long option without a name"
        ).entrySet();
    }

    private static TestSpec.Builder build(String value) {
        return new TestSpec.Builder().value(value);
    }
}
