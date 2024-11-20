/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.util.ArrayList;
import java.util.List;
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
    @MethodSource("validData")
    public void testValid(TestConfig cfg) {
        var dv = cfg.createVersion.apply(cfg.input());
        assertEquals(cfg.expectedSuffix(), dv.getUnprocessedSuffix());
        assertEquals(cfg.expectedComponentCount(), dv.getComponents().length);
        assertEquals(cfg.expectedToComponent(), dv.toComponentsString());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> validData() {
        List<TestConfig> data = new ArrayList<>();
        for (var type : Type.values()) {
            data.addAll(List.of(new TestConfig("1.0", type, 2),
                    new TestConfig("1", type, 1),
                    new TestConfig("2.20034.045", type, 3, "2.20034.45"),
                    new TestConfig("2.234.0", type, 3),
                    new TestConfig("0", type, 1),
                    new TestConfig("0.1", type, 2),
                    new TestConfig("9".repeat(1000), type, 1),
                    new TestConfig("00.0.0", type, 3, "0.0.0")
            ));
        }

        data.addAll(List.of(TestConfig.lazy("1.-1", ".-1", 1, "1"),
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

        return data.stream().map(org.junit.jupiter.params.provider.Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("invalidData")
    public void testInvalid(String str) {
        assertThrowsExactly(IllegalArgumentException.class, () -> new DottedVersion(str));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> invalidData() {
        return Stream.of(
                "1.-1",
                "5.",
                "4.2.",
                "3..2",
                "2.a",
                "0a",
                ".",
                " ",
                " 1",
                "1. 2",
                "+1",
                "-1",
                "-0",
                "+0"
        ).map(org.junit.jupiter.params.provider.Arguments::of);
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    public void testNull(Type type) {
        assertThrowsExactly(NullPointerException.class, () -> type.createVersion.apply(null));
    }

    @Test
    public void testEmptyGreey() {
        assertThrowsExactly(IllegalArgumentException.class, () -> DottedVersion.greedy(""), "Version may not be empty string");
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
    public void testEqualsLaxy() {
        assertTrue(DottedVersion.lazy("3.6+67").equals(DottedVersion.lazy("3.6+67")));
        assertFalse(DottedVersion.lazy("3.6+67").equals(DottedVersion.lazy("3.6+067")));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> compareData() {
        List<Object[]> data = new ArrayList<>();
        for (var greedy : List.of(true, false)) {
            data.addAll(List.of(new Object[][] {
                { greedy, "00.0.0", "0", 0 },
                { greedy, "00.0.0", "0.000", 0 },
                { greedy, "0.035", "0.0035", 0 },
                { greedy, "0.035", "0.0035.0", 0 },
                { greedy, "1", "1", 0 },
                { greedy, "2", "2.0", 0 },
                { greedy, "2.00", "2.0", 0 },
                { greedy, "1.2.3.4", "1.2.3.4.5", -1 },
                { greedy, "1.2.3.4", "1.2.3.4.0.1", -1 },
                { greedy, "34", "33", 1 },
                { greedy, "34.0.78", "34.1.78", -1 }
            }));
        }

        data.addAll(List.of(new Object[][] {
            { false, "", "1", -1 },
            { false, "", "0", 0 },
            { false, "0", "", 0 },
            { false, "1.2.4-R4", "1.2.4-R5", 0 },
            { false, "1.2.4.-R4", "1.2.4.R5", 0 },
            { false, "7+1", "7+4", 0 },
            { false, "2+14", "2-14", 0 },
            { false, "23.4.RC4", "23.3.RC10", 1 },
            { false, "77."  + "9".repeat(1000), "77." + "9".repeat(1000 -1) + "8", 1 },
        }));

        return data.stream().map(org.junit.jupiter.params.provider.Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("compareData")
    public void testCompare(boolean greedy, String version1, String version2, int expectedResult) {
        final Function<String, DottedVersion> createTestee;
        if (greedy) {
            createTestee = DottedVersion::greedy;
        } else {
            createTestee = DottedVersion::lazy;
        }

        final int actualResult = compare(createTestee, version1, version2);
        assertEquals(expectedResult, actualResult);

        final int actualNegateResult = compare(createTestee, version2, version1);
        assertEquals(actualResult, -1 * actualNegateResult);
    }

    private int compare(Function<String, DottedVersion> createTestee, String x, String y) {
        int result = DottedVersion.compareComponents(createTestee.apply(x), createTestee.apply(y));

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
