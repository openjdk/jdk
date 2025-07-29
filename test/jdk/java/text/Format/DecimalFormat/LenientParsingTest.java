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
 * @bug 8363972
 * @summary Unit tests for lenient parsing
 * @modules jdk.localedata
 * @run junit LenientParsingTest
 */

import java.text.CompactNumberFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LenientParsingTest {
    private static final Locale FINNISH = Locale.of("fi");
    private static final DecimalFormatSymbols DFS =
        new DecimalFormatSymbols(Locale.ROOT);

    // "parseLenient" data from CLDR v47. These data are subject to change
    private static Stream<String> minus() {
        return Stream.of(
            "－",    // U+FF0D Fullwidth Hyphen-Minus
            "﹣",    // U+FE63 Small Hyphen-Minus
            "‐",    // U+2010 Hyphen
            "‑",    // U+2011 Non-Breaking Hyphen
            "‒",    // U+2012 Figure Dash
            "–",    // U+2013 En Dash
            "−",    // U+2212 Minus Sign
            "⁻",    // U+207B Superscript Minus
            "₋",    // U+208B Subscript Minus
            "➖"     // U+2796 Heavy Minus Sign
        );
    }

    @Test
    void testFinnishMinus() {
        // originally reported in JDK-8189097
        // Should not throw a ParseException
        assertDoesNotThrow(() -> NumberFormat.getInstance(FINNISH).parse("-1,5"));
    }

    @Test
    void testFinnishMinusStrict() {
        // Should throw a ParseException
        var nf = NumberFormat.getInstance(FINNISH);
        nf.setStrict(true);
        assertThrows(ParseException.class, () -> nf.parse("-1,5"));
    }

    @Nested
    class DecimalFormatTest {
        private static final String PREFIX = "#";
        private static final String SUFFIX = "#;#-";

        @ParameterizedTest
        @MethodSource("LenientParsingTest#minus")
        public void testLenientPrefix(String sign) throws ParseException {
            var df = new DecimalFormat(PREFIX, DFS);
            df.setStrict(false);
            assertEquals(df.format(df.parse(sign + "1")), "-1");
        }

        @ParameterizedTest
        @MethodSource("LenientParsingTest#minus")
        public void testLenientSuffix(String sign) throws ParseException {
            var df = new DecimalFormat(SUFFIX, DFS);
            df.setStrict(false);
            assertEquals(df.format(df.parse("1" + sign)), "1-");
        }

        @ParameterizedTest
        @MethodSource("LenientParsingTest#minus")
        public void testStrictPrefix(String sign) {
            var df = new DecimalFormat(PREFIX, DFS);
            df.setStrict(true);
            assertThrows(ParseException.class, () -> df.parse(sign + "1"));
        }

        @ParameterizedTest
        @MethodSource("LenientParsingTest#minus")
        public void testStrictSuffix(String sign) {
            var df = new DecimalFormat(SUFFIX, DFS);
            df.setStrict(true);
            assertThrows(ParseException.class, () -> df.parse("1" + sign));
        }
    }

    @Nested
    class CompactNumberFormatTest {
        private static final String[] PREFIX = {"0"};
        private static final String[] SUFFIX = {"0+;0-"};

        @ParameterizedTest
        @MethodSource("LenientParsingTest#minus")
        public void testLenientPrefix(String sign) throws ParseException {
            var cnf = new CompactNumberFormat("0", DFS, PREFIX);
            cnf.setStrict(false);
            assertEquals(cnf.format(cnf.parse(sign + "1")), "-1");
        }

        @ParameterizedTest
        @MethodSource("LenientParsingTest#minus")
        public void testLenientSuffix(String sign) throws ParseException {
            var cnf = new CompactNumberFormat("0", DFS, SUFFIX);
            cnf.setStrict(false);
            assertEquals(cnf.format(cnf.parse("1" + sign)), "1-");
        }

        @ParameterizedTest
        @MethodSource("LenientParsingTest#minus")
        public void testStrictPrefix(String sign) {
            var cnf = new CompactNumberFormat("0", DFS, PREFIX);
            cnf.setStrict(true);
            assertThrows(ParseException.class, () -> cnf.parse(sign + "1"));
        }

        @ParameterizedTest
        @MethodSource("LenientParsingTest#minus")
        public void testStrictSuffix(String sign) {
            var cnf = new CompactNumberFormat("0", DFS, SUFFIX);
            cnf.setStrict(true);
            assertThrows(ParseException.class, () -> cnf.parse("1" + sign));
        }
    }
}
