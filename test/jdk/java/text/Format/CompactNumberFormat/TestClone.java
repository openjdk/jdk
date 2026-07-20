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
/*
 * @test
 * @bug 8368328
 * @summary Tests if CompactNumberFormat.clone() creates an independent object
 * @run junit/othervm --add-opens java.base/java.text=ALL-UNNAMED TestClone
 */

import java.lang.invoke.MethodHandles;
import java.text.CompactNumberFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class TestClone {
    // Concurrently parse numbers using cloned instances as originally
    // reported in the bug. This test could produce false negative results,
    // depending on the testing environment
    @Test
    void randomAccessTest() {
        var original =
            NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        var threads = IntStream.range(0, 10)
            .mapToObj(num -> new Thread(() -> {
                var clone = (NumberFormat) original.clone();
                for (int i = 0; i < 1000; i++) {
                    assertDoesNotThrow(() ->
                        assertEquals(num, clone.parse(String.valueOf(num)).intValue()));
                }
            })).toList();
        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        });
    }

    private static Stream<Arguments> referenceFields() throws ClassNotFoundException {
        return Stream.of(
            Arguments.of("compactPatterns", String[].class),
            Arguments.of("symbols", DecimalFormatSymbols.class),
            Arguments.of("decimalFormat", DecimalFormat.class),
            Arguments.of("defaultDecimalFormat", DecimalFormat.class),
            Arguments.of("digitList", Class.forName("java.text.DigitList"))
        );
    }
    // Explicitly checks if the cloned object has its own references for
    // "compactPatterns", "symbols", "decimalFormat", "defaultDecimalFormat",
    // and "digitList"
    @ParameterizedTest
    @MethodSource("referenceFields")
    void whiteBoxTest(String fieldName, Class<?> type) throws Throwable {
        var original = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        var clone = original.clone();
        var lookup = MethodHandles.privateLookupIn(CompactNumberFormat.class, MethodHandles.lookup());

        assertNotSame(lookup.findGetter(CompactNumberFormat.class, fieldName, type).invoke(original),
            lookup.findGetter(CompactNumberFormat.class, fieldName, type).invoke(clone));
    }
}
