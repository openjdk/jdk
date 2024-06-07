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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DottedVersionTest {

    public DottedVersionTest(boolean greedy) {
        this.greedy = greedy;
        if (greedy) {
            createTestee = DottedVersion::greedy;
        } else {
            createTestee = DottedVersion::lazy;
        }
    }

    @Parameterized.Parameters
    public static List<Object[]> data() {
        return List.of(new Object[] { true }, new Object[] { false });
    }

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private static class CtorTester {

        CtorTester(String input, boolean greedy, String expectedSuffix,
                int expectedComponentCount, String expectedToComponent) {
            this.input = input;
            this.greedy = greedy;
            this.expectedSuffix = expectedSuffix;
            this.expectedComponentCount = expectedComponentCount;
            this.expectedToComponent = expectedToComponent;
        }

        CtorTester(String input, boolean greedy, int expectedComponentCount,
                String expectedToComponent) {
            this(input, greedy, "", expectedComponentCount, expectedToComponent);
        }

        CtorTester(String input, boolean greedy, int expectedComponentCount) {
            this(input, greedy, "", expectedComponentCount, input);
        }

        static CtorTester greedy(String input, int expectedComponentCount,
                String expectedToComponent) {
            return new CtorTester(input, true, "", expectedComponentCount, expectedToComponent);
        }

        static CtorTester greedy(String input, int expectedComponentCount) {
            return new CtorTester(input, true, "", expectedComponentCount, input);
        }

        static CtorTester lazy(String input, String expectedSuffix, int expectedComponentCount,
                String expectedToComponent) {
            return new CtorTester(input, false, expectedSuffix, expectedComponentCount,
                    expectedToComponent);
        }

        void run() {
            DottedVersion dv;
            if (greedy) {
                dv = DottedVersion.greedy(input);
            } else {
                dv = DottedVersion.lazy(input);
            }

            assertEquals(expectedSuffix, dv.getUnprocessedSuffix());
            assertEquals(expectedComponentCount, dv.getComponents().length);
            assertEquals(expectedToComponent, dv.toComponentsString());
        }

        private final String input;
        private final boolean greedy;
        private final String expectedSuffix;
        private final int expectedComponentCount;
        private final String expectedToComponent;
    }

    @Test
    public void testValid() {
        final List<CtorTester> validStrings = List.of(
                new CtorTester("1.0", greedy, 2),
                new CtorTester("1", greedy, 1),
                new CtorTester("2.20034.045", greedy, 3, "2.20034.45"),
                new CtorTester("2.234.0", greedy, 3),
                new CtorTester("0", greedy, 1),
                new CtorTester("0.1", greedy, 2),
                new CtorTester("9".repeat(1000), greedy, 1),
                new CtorTester("00.0.0", greedy, 3, "0.0.0")
        );

        final List<CtorTester> validLazyStrings;
        if (greedy) {
            validLazyStrings = Collections.emptyList();
        } else {
            validLazyStrings = List.of(
                    CtorTester.lazy("1.-1", ".-1", 1, "1"),
                    CtorTester.lazy("5.", ".", 1, "5"),
                    CtorTester.lazy("4.2.", ".", 2, "4.2"),
                    CtorTester.lazy("3..2", "..2", 1, "3"),
                    CtorTester.lazy("3......2", "......2", 1, "3"),
                    CtorTester.lazy("2.a", ".a", 1, "2"),
                    CtorTester.lazy("a", "a", 0, ""),
                    CtorTester.lazy("2..a", "..a", 1, "2"),
                    CtorTester.lazy("0a", "a", 1, "0"),
                    CtorTester.lazy("120a", "a", 1, "120"),
                    CtorTester.lazy("120abc", "abc", 1, "120"),
                    CtorTester.lazy(".", ".", 0, ""),
                    CtorTester.lazy("....", "....", 0, ""),
                    CtorTester.lazy(" ", " ", 0, ""),
                    CtorTester.lazy(" 1", " 1", 0, ""),
                    CtorTester.lazy("678. 2", ". 2", 1, "678"),
                    CtorTester.lazy("+1", "+1", 0, ""),
                    CtorTester.lazy("-1", "-1", 0, ""),
                    CtorTester.lazy("-0", "-0", 0, ""),
                    CtorTester.lazy("+0", "+0", 0, "")
            );
        }

        Stream.concat(validStrings.stream(), validLazyStrings.stream()).forEach(CtorTester::run);
    }

    @Test
    public void testNull() {
        exceptionRule.expect(NullPointerException.class);
        createTestee.apply(null);
    }

    @Test
    public void testEmpty() {
        if (greedy) {
            exceptionRule.expect(IllegalArgumentException.class);
            exceptionRule.expectMessage("Version may not be empty string");
            createTestee.apply("");
        } else {
            assertEquals(0, createTestee.apply("").getComponents().length);
        }
    }

    @Test
    public void testEquals() {
        DottedVersion dv = createTestee.apply("1.0");
        assertFalse(dv.equals(null));
        assertFalse(dv.equals(Integer.valueOf(1)));

        for (var ver : List.of("3", "3.4", "3.0.0")) {
            DottedVersion a = createTestee.apply(ver);
            DottedVersion b = createTestee.apply(ver);
            assertTrue(a.equals(b));
            assertTrue(b.equals(a));
        }

        if (!greedy) {
            assertTrue(createTestee.apply("3.6+67").equals(createTestee.apply("3.6+67")));
            assertFalse(createTestee.apply("3.6+67").equals(createTestee.apply("3.6+067")));
        }
    }

    private final boolean greedy;
    private final Function<String, DottedVersion> createTestee;
}
