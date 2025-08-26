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
 * @summary Unit tests for lenient minus parsing
 * @modules jdk.localedata
 *          java.base/java.text:+open
 * @run junit LenientMinusSignTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandles;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LenientMinusSignTest {
    private static final Locale FINNISH = Locale.of("fi");
    private static final DecimalFormatSymbols DFS =
        new DecimalFormatSymbols(Locale.ROOT);
    private static final String MINUS_PATTERN = "\u002D";

    // "parseLenient" data from CLDR v47. These data are subject to change
    private static Stream<String> minus() {
        return Stream.of(
            MINUS_PATTERN,     // "-" Hyphen-Minus
            "\uFF0D",          // "－" Fullwidth Hyphen-Minus
            "\uFE63",          // "﹣" Small Hyphen-Minus
            "\u2010",          // "‐" Hyphen
            "\u2011",          // "‑" Non-Breaking Hyphen
            "\u2012",          // "‒" Figure Dash
            "\u2013",          // "–" En Dash
            "\u2212",          // "−" Minus Sign
            "\u207B",          // "⁻" Superscript Minus
            "\u208B",          // "₋" Subscript Minus
            "\u2796"           // "➖" Heavy Minus Sign
        );
    }

    @Test
    void testFinnishMinus() throws ParseException {
        // originally reported in JDK-8189097
        // Should not throw a ParseException
        assertEquals(NumberFormat.getInstance(FINNISH).parse(MINUS_PATTERN + "1,5"), -1.5);
    }

    @Test
    void testFinnishMinusStrict() {
        // Should throw a ParseException
        var nf = NumberFormat.getInstance(FINNISH);
        nf.setStrict(true);
        assertThrows(ParseException.class, () -> nf.parse(MINUS_PATTERN + "1,5"));
    }

    @Test
    void testReadObject() throws IOException, ClassNotFoundException, ParseException {
        // check if deserialized NF works with lenient minus. Using the Finnish example
        var nf = NumberFormat.getInstance(FINNISH);
        NumberFormat nfDeser;
        byte[] serialized;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(nf);
            out.flush();
            serialized = bos.toByteArray();
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            nfDeser = (NumberFormat) in.readObject();
        }
        assertEquals(nfDeser.parse(MINUS_PATTERN + "1,5"), -1.5);
    }

    // White box test. modifies the private `lenientMinusSigns` field in the DFS
    @Test
    void testSupplementary() throws IllegalAccessException, NoSuchFieldException, ParseException {
        var dfs = new DecimalFormatSymbols(Locale.ROOT);
        MethodHandles.privateLookupIn(DecimalFormatSymbols.class, MethodHandles.lookup())
            .findVarHandle(DecimalFormatSymbols.class, "lenientMinusSigns", String.class)
            .set(dfs, "-🙂");
        // Direct match. Should succeed
        var df = new DecimalFormat("#.#;🙂#.#", dfs);
        assertEquals(df.parse("🙂1.5"), -1.5);

        // Fail if the lengths of negative prefixes differ
        assertThrows(ParseException.class, () -> df.parse("-1.5"));
        var df2= new DecimalFormat("#.#;-#.#", dfs);
        assertThrows(ParseException.class, () -> df2.parse("🙂1.5"));
    }

    @Nested
    class DecimalFormatTest {
        private static final String PREFIX = "+#;-#";
        private static final String SUFFIX = "#+;#-";
        private static final String LONG_PREFIX = "pos#;-neg#";
        private static final String LONG_SUFFIX = "#pos;#neg-";

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testLenientPrefix(String sign) throws ParseException {
            var df = new DecimalFormat(PREFIX, DFS);
            df.setStrict(false);
            assertEquals(MINUS_PATTERN + "1", df.format(df.parse(sign + "1")));
        }

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testLenientSuffix(String sign) throws ParseException {
            var df = new DecimalFormat(SUFFIX, DFS);
            df.setStrict(false);
            assertEquals("1" + MINUS_PATTERN, df.format(df.parse("1" + sign)));
        }

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testStrictPrefix(String sign) throws ParseException {
            var df = new DecimalFormat(PREFIX, DFS);
            df.setStrict(true);
            if (sign.equals(MINUS_PATTERN)) {
                assertEquals(MINUS_PATTERN + "1", df.format(df.parse(sign + "1")));
            } else {
                assertThrows(ParseException.class, () -> df.parse(sign + "1"));
            }
        }

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testStrictSuffix(String sign) throws ParseException {
            var df = new DecimalFormat(SUFFIX, DFS);
            df.setStrict(true);
            if (sign.equals(MINUS_PATTERN)) {
                assertEquals("1" + MINUS_PATTERN, df.format(df.parse("1" + sign)));
            } else {
                assertThrows(ParseException.class, () -> df.parse("1" + sign));
            }
        }

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testLongPrefix(String sign) throws ParseException {
            var df = new DecimalFormat(LONG_PREFIX, DFS);
            assertEquals(MINUS_PATTERN + "neg1", df.format(df.parse(sign + "neg1")));
        }

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testLongSuffix(String sign) throws ParseException {
            var df = new DecimalFormat(LONG_SUFFIX, DFS);
            assertEquals("1neg" + MINUS_PATTERN, df.format(df.parse("1neg" + sign)));
        }
    }

    @Nested
    class CompactNumberFormatTest {
        private static final String[] PREFIX = {"+0;-0"};
        private static final String[] SUFFIX = {"0+;0-"};
        private static final String[] LONG_PREFIX = {"pos0;-neg0"};
        private static final String[] LONG_SUFFIX = {"0pos;0neg-"};

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testLenientPrefix(String sign) throws ParseException {
            var cnf = new CompactNumberFormat("0", DFS, PREFIX);
            cnf.setStrict(false);
            assertEquals(MINUS_PATTERN + "1", cnf.format(cnf.parse(sign + "1")));
        }

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testLenientSuffix(String sign) throws ParseException {
            var cnf = new CompactNumberFormat("0", DFS, SUFFIX);
            cnf.setStrict(false);
            assertEquals("1" + MINUS_PATTERN, cnf.format(cnf.parse("1" + sign)));
        }

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testStrictPrefix(String sign) throws ParseException {
            var cnf = new CompactNumberFormat("0", DFS, PREFIX);
            cnf.setStrict(true);
            if (sign.equals(MINUS_PATTERN)) {
                assertEquals(MINUS_PATTERN + "1", cnf.format(cnf.parse(sign + "1")));
            } else {
                assertThrows(ParseException.class, () -> cnf.parse(sign + "1"));
            }
        }

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testStrictSuffix(String sign) throws ParseException {
            var cnf = new CompactNumberFormat("0", DFS, SUFFIX);
            cnf.setStrict(true);
            if (sign.equals(MINUS_PATTERN)) {
                assertEquals("1" + MINUS_PATTERN, cnf.format(cnf.parse("1" + sign)));
            } else {
                assertThrows(ParseException.class, () -> cnf.parse("1" + sign));
            }
        }

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testLongPrefix(String sign) throws ParseException {
            var cnf = new CompactNumberFormat("0", DFS, LONG_PREFIX);
            assertEquals(MINUS_PATTERN + "neg1", cnf.format(cnf.parse(sign + "neg1")));
        }

        @ParameterizedTest
        @MethodSource("LenientMinusSignTest#minus")
        public void testLongSuffix(String sign) throws ParseException {
            var cnf = new CompactNumberFormat("0", DFS, LONG_SUFFIX);
            assertEquals("1neg" + MINUS_PATTERN, cnf.format(cnf.parse( "1neg" + sign)));
        }
    }
}
