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

package jdk.jpackage.internal.summary;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SummaryTest {

    @Test
    public void test_empty() {
        assertSummary(new Summary());
    }

    @ParameterizedTest
    @MethodSource
    public void test_stable_order(List<String> expected, List<Map.Entry<? extends SummaryItem, String>> entries) {

        var summary = new Summary();

        for (var e : entries) {
            summary.put(e.getKey(), e.getValue());
        }

        assertSummary(summary, expected);
    }

    private static Stream<Arguments> test_stable_order() {
        var items = List.of(
                Map.entry(TestProperty.FOO, "foo"),
                Map.entry(TestProperty.BAR, "bar"),
                Map.entry(TestWarning.WFOO, "foo!"),
                Map.entry(TestWarning.WBAR, "bar!")
        );

        var expected = items.stream().map(Map.Entry::getValue).toList();

        // Hardcoded permutations
        return Stream.of(
                List.of(0,1,2,3),
                List.of(1,0,2,3),
                List.of(2,0,1,3),
                List.of(0,2,1,3),
                List.of(1,2,0,3),
                List.of(2,1,0,3),
                List.of(2,1,3,0),
                List.of(1,2,3,0),
                List.of(3,2,1,0),
                List.of(2,3,1,0),
                List.of(1,3,2,0),
                List.of(3,1,2,0),
                List.of(3,0,2,1),
                List.of(0,3,2,1),
                List.of(2,3,0,1),
                List.of(3,2,0,1),
                List.of(0,2,3,1),
                List.of(2,0,3,1),
                List.of(1,0,3,2),
                List.of(0,1,3,2),
                List.of(3,1,0,2),
                List.of(1,3,0,2),
                List.of(0,3,1,2),
                List.of(3,0,1,2)
        ).map(indexes -> {
            return indexes.stream().map(items::get).toList();
        }).map(v -> {
            return Arguments.of(expected, v);
        });
    }

    private static void assertSummary(Summary summary, String... expectedValues) {
        assertSummary(summary, List.of(expectedValues));
    }

    private static void assertSummary(Summary summary, List<String> expectedValues) {
        var curExpectedTail = expectedValues.iterator();
        Consumer<String> sink = line -> {
            var expectedTail = curExpectedTail.next();
            assertTrue(line.endsWith(expectedTail), String.format("Assert summary line [%s] ends with [%s] substring", line, expectedTail));
        };
        summary.print(sink, sink);
    }

    private enum TestProperty implements Property {
        FOO,
        BAR,
        ;

        @Override
        public Optional<String> valueFormatter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String formatValue(Object... valueFormatArgs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String formatLabel() {
            return name();
        }
    }

    private enum TestWarning implements Warning {
        WFOO,
        WBAR,
        ;

        @Override
        public Optional<String> valueFormatter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String formatValue(Object... valueFormatArgs) {
            throw new UnsupportedOperationException();
        }
    }
}
