/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.model;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class DottedVersionTest {

    public record TestConfig(String input,
            Function<String, DottedVersion> createVersion, String expectedSuffix,
            int expectedComponentCount, String expectedToComponent) {

        TestConfig(String input, Type type, int expectedComponentCount, String expectedToComponent) {
            this(input, type.createVersion, "", expectedComponentCount, expectedToComponent);
        }

        TestConfig(String input, Type type, int expectedComponentCount) {
            this(input, type.createVersion, "", expectedComponentCount, input);
        }

        static TestConfig greedy(String input, int expectedComponentCount, String expectedToComponent) {
            return new TestConfig(input, Type.GREEDY.createVersion, "", expectedComponentCount, expectedToComponent);
        }

        static TestConfig greedy(String input, int expectedComponentCount) {
            return new TestConfig(input, Type.GREEDY.createVersion, "", expectedComponentCount, input);
        }

        static TestConfig lazy(String input, String expectedSuffix, int expectedComponentCount, String expectedToComponent) {
            return new TestConfig(input, Type.LAZY.createVersion, expectedSuffix, expectedComponentCount, expectedToComponent);
        }
    }

    @ParameterizedTest
    @MethodSource
    public void testValid(TestConfig cfg) {
        var dv = cfg.createVersion.apply(cfg.input());
        assertEquals(cfg.expectedSuffix(), dv.getUnprocessedSuffix());
        assertEquals(cfg.expectedComponentCount(), dv.getComponents().length);
        assertEquals(cfg.expectedToComponent(), dv.toComponentsString());
    }

    private static List<TestConfig> testValid() {
        List<TestConfig> data = new ArrayList<>();
        for (var type : Type.values()) {
            data.addAll(List.of(
                    new TestConfig("1.0", type, 2),
                    new TestConfig("1", type, 1),
                    new TestConfig("2.20034.045", type, 3, "2.20034.45"),
                    new TestConfig("2.234.0", type, 3),
                    new TestConfig("0", type, 1),
                    new TestConfig("0.1", type, 2),
                    new TestConfig("9".repeat(1000), type, 1),
                    new TestConfig("00.0.0", type, 3, "0.0.0")
            ));
        }

        data.addAll(List.of(
                TestConfig.lazy("1.-1", ".-1", 1, "1"),
                TestConfig.lazy("5.", ".", 1, "5"),
                TestConfig.lazy("4.2.", ".", 2, "4.2"),
                TestConfig.lazy("3..2", "..2", 1, "3"),
                TestConfig.lazy("3......2", "......2", 1, "3"),
                TestConfig.lazy("2.a", ".a", 1, "2"),
                TestConfig.lazy("a", "a", 0, ""),
                TestConfig.lazy("2..a", "..a", 1, "2"),
                TestConfig.lazy("0a", "a", 1, "0"),
                TestConfig.lazy("120a", "a", 1, "120"),
                TestConfig.lazy("120abc", "abc", 1, "120"),
                TestConfig.lazy(".", ".", 0, ""),
                TestConfig.lazy("....", "....", 0, ""),
                TestConfig.lazy(" ", " ", 0, ""),
                TestConfig.lazy(" 1", " 1", 0, ""),
                TestConfig.lazy("678. 2", ". 2", 1, "678"),
                TestConfig.lazy("+1", "+1", 0, ""),
                TestConfig.lazy("-1", "-1", 0, ""),
                TestConfig.lazy("-0", "-0", 0, ""),
                TestConfig.lazy("+0", "+0", 0, "")
        ));

        return data;
    }

    record InvalidVersionTestSpec(String version, String invalidComponent) {
        public InvalidVersionTestSpec {
            Objects.requireNonNull(version);
            Objects.requireNonNull(invalidComponent);
        }

        InvalidVersionTestSpec(String version) {
            this(version, "");
        }

        void run() {
            final String expectedErrorMsg;
            if (invalidComponent.isEmpty()) {
                expectedErrorMsg = MessageFormat.format(I18N.getString("error.version-string-zero-length-component"), version);
            } else {
                expectedErrorMsg = MessageFormat.format(I18N.getString("error.version-string-invalid-component"), version, invalidComponent);
            }

            final var ex = assertThrowsExactly(IllegalArgumentException.class, () -> DottedVersion.greedy(version));

            assertEquals(expectedErrorMsg, ex.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource
    public void testInvalid(InvalidVersionTestSpec testSpec) {
        testSpec.run();
    }

    private static Stream<InvalidVersionTestSpec> testInvalid() {
        return Stream.of(
                new InvalidVersionTestSpec("1.-1", "-1"),
                new InvalidVersionTestSpec("5."),
                new InvalidVersionTestSpec("4.2."),
                new InvalidVersionTestSpec("3..2", ".2"),
                new InvalidVersionTestSpec("3...2", "..2"),
                new InvalidVersionTestSpec("2.a", "a"),
                new InvalidVersionTestSpec("0a", "a"),
                new InvalidVersionTestSpec("1.0a", "0a"),
                new InvalidVersionTestSpec(".", "."),
                new InvalidVersionTestSpec("..", ".."),
                new InvalidVersionTestSpec(".a.b", ".a.b"),
                new InvalidVersionTestSpec(".1.2", ".1.2"),
                new InvalidVersionTestSpec(" ", " "),
                new InvalidVersionTestSpec(" 1", " 1"),
                new InvalidVersionTestSpec("1. 2", " 2"),
                new InvalidVersionTestSpec("+1", "+1"),
                new InvalidVersionTestSpec("-1", "-1"),
                new InvalidVersionTestSpec("-0", "-0"),
                new InvalidVersionTestSpec("+0", "+0")
        );
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    public void testNull(Type type) {
        assertThrowsExactly(NullPointerException.class, () -> type.createVersion.apply(null));
    }

    @Test
    public void testEmptyGreedy() {
        final var ex = assertThrowsExactly(IllegalArgumentException.class, () -> DottedVersion.greedy(""));
        assertEquals(I18N.getString("error.version-string-empty"), ex.getMessage());
    }

    @Test
    public void testEmptyLazy() {
        assertEquals(0, DottedVersion.lazy("").getComponents().length);
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    public void testEquals(Type type) {
        DottedVersion dv = type.createVersion.apply("1.0");
        assertFalse(dv.equals(null));
        assertFalse(dv.equals(1));
        assertFalse(dv.equals(dv.toString()));

        for (var ver : List.of("3", "3.4", "3.0.0")) {
            DottedVersion a = type.createVersion.apply(ver);
            DottedVersion b = type.createVersion.apply(ver);
            assertTrue(a.equals(b));
            assertTrue(b.equals(a));
        }
    }

    @Test
    public void testEqualsLazy() {
        assertTrue(DottedVersion.lazy("3.6+67").equals(DottedVersion.lazy("3.6+67")));
        assertFalse(DottedVersion.lazy("3.6+67").equals(DottedVersion.lazy("3.6+067")));
    }

    private static List<Object[]> testCompare() {
        List<Object[]> data = new ArrayList<>();
        for (var type : Type.values()) {
            data.addAll(List.of(new Object[][] {
                { type, "00.0.0", "0", 0 },
                { type, "00.0.0", "0.000", 0 },
                { type, "0.035", "0.0035", 0 },
                { type, "0.035", "0.0035.0", 0 },
                { type, "1", "1", 0 },
                { type, "2", "2.0", 0 },
                { type, "2.00", "2.0", 0 },
                { type, "1.2.3.4", "1.2.3.4.5", -1 },
                { type, "1.2.3.4", "1.2.3.4.0.1", -1 },
                { type, "34", "33", 1 },
                { type, "34.0.78", "34.1.78", -1 }
            }));
        }

        data.addAll(List.of(new Object[][] {
            { Type.LAZY, "", "1", -1 },
            { Type.LAZY, "", "0", 0 },
            { Type.LAZY, "0", "", 0 },
            { Type.LAZY, "1.2.4-R4", "1.2.4-R5", 0 },
            { Type.LAZY, "1.2.4.-R4", "1.2.4.R5", 0 },
            { Type.LAZY, "7+1", "7+4", 0 },
            { Type.LAZY, "2+14", "2-14", 0 },
            { Type.LAZY, "23.4.RC4", "23.3.RC10", 1 },
            { Type.LAZY, "77."  + "9".repeat(1000), "77." + "9".repeat(1000 -1) + "8", 1 },
        }));

        return data;
    }

    @ParameterizedTest
    @MethodSource
    public void testCompare(Type type, String version1, String version2, int expectedResult) {
        final int actualResult = compare(type, version1, version2);
        assertEquals(expectedResult, actualResult);

        final int actualNegateResult = compare(type, version2, version1);
        assertEquals(actualResult, -1 * actualNegateResult);
    }

    private int compare(Type type, String x, String y) {
        int result = DottedVersion.compareComponents(type.createVersion.apply(x), type.createVersion.apply(y));

        if (result < 0) {
            return -1;
        }

        if (result > 0) {
            return 1;
        }

        return 0;
    }

    public enum Type {
        GREEDY(DottedVersion::greedy),
        LAZY(DottedVersion::lazy);

        Type(Function<String, DottedVersion> createVersion) {
            this.createVersion = createVersion;
        }

        private final Function<String, DottedVersion> createVersion;
    }
}
