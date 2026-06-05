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

/**
 * @test
 * @summary tests unicode case-folding based String comparison and equality
 * @bug 4397357
 * @library /lib/testlibrary/java/lang
 * @modules java.base/jdk.internal.lang:+open
 * @run junit/othervm
 * UnicodeCaseFoldingTest
 */

import java.nio.file.Files;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.ArrayList;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jdk.internal.lang.CaseFolding;

public class UnicodeCaseFoldingTest {

    @Test
    void testAllCommnFullCodePointsListedInCaseFoldinigTxt() throws Throwable {
        var filter = "^.*; [CF]; .*$";  // C=common, F=full, for full case folding
        var results = Files.lines(UCDFiles.CASEFOLDING)
                .filter(line -> !line.startsWith("#") && line.matches(filter))
                .map(line -> {
                    var fields = line.split("; ");
                    var cp = Integer.parseInt(fields[0], 16);
                    fields = fields[2].trim().split(" ");
                    var folding = new int[fields.length];
                    for (int i = 0; i < folding.length; i++) {
                        folding[i] = Integer.parseInt(fields[i], 16);
                    }
                    var source = new String(Character.toChars(cp));
                    var expected = new String(folding, 0, folding.length);
                    // (1) Verify the folding result matches expected
                    assertEquals(expected, foldCase(source), "CaseFolding.fold(): ");

                    // (2) Verify compareToFoldCase() result
                    assertEquals(0, source.compareToFoldCase(expected), "source.compareToFoldCase(expected)");
                    assertEquals(0, expected.compareToFoldCase(source), "expected.compareToFoldCase(source)");

                    // (3) Verify equalsFoldCase() result
                    assertEquals(true, source.equalsFoldCase(expected), "source.equalsFoldCase(expected)");
                    assertEquals(true, expected.equalsFoldCase(source), "expected.equalsFoldCase(source)");
                    return null;
                })
                .filter(error -> error != null)
                .toArray();
        assertEquals(0, results.length);
    }

    @Test
    void testAllSimpleCodePointsListedInCaseFoldinigTxt() throws Throwable {
        // S=simple, for simple case folding. The simple case folding should still matches
        var filter = "^.*; [S]; .*$";
        var results = Files.lines(UCDFiles.CASEFOLDING)
                .filter(line -> !line.startsWith("#") && line.matches(filter))
                .map(line -> {
                    var fields = line.split("; ");
                    var cp = Integer.parseInt(fields[0], 16);
                    fields = fields[2].trim().split(" ");
                    var folding = new int[fields.length];
                    for (int i = 0; i < folding.length; i++) {
                        folding[i] = Integer.parseInt(fields[i], 16);
                    }
                    var source = new String(Character.toChars(cp));
                    var expected = new String(folding, 0, folding.length);

                    // (1) Verify compareToFoldCase() result
                    assertEquals(0, source.compareToFoldCase(expected), "source.compareToFoldCase(expected)");
                    assertEquals(0, expected.compareToFoldCase(source), "expected.compareToFoldCase(source)");

                    // (2) Verify equalsFoldCase() result
                    assertEquals(true, source.equalsFoldCase(expected), "source.equalsFoldCase(expected)");
                    assertEquals(true, expected.equalsFoldCase(source), "expected.equalsFoldCase(source)");
                    return null;
                })
                .filter(error -> error != null)
                .toArray();
        assertEquals(0, results.length);
    }

    @Test
    public void testAllCodePointsFoldToThemselvesIfNotListed() throws Exception {
        // Collect all code points that appear in CaseFolding.txt
        var listed = Files.lines(UCDFiles.CASEFOLDING)
                .filter(line -> !line.startsWith("#") && line.matches("^.*; [CF]; .*$"))
                .map(line -> Integer.parseInt(line.split("; ")[0], 16))
                .collect(Collectors.toSet());

        var failures = new ArrayList<String>();

        // Scan BMP + Supplementary Plane 1 (U+0000..U+1FFFF)
        for (int cp = Character.MIN_CODE_POINT; cp <= 0x1FFFF; cp++) {
            if (!Character.isDefined(cp)) {
                continue;     // skip undefined
            }
            if (Character.isSurrogate((char) cp)) {
                continue; // skip surrogate code units
            }
            if (listed.contains(cp)) {
                continue;          // already tested separately
            }
            String s = new String(Character.toChars(cp));
            String folded = foldCase(s);
            if (!s.equals(folded)) {
                failures.add(String.format("Unexpected folding: U+%04X '%s' → '%s'", cp, s, folded));
            }
        }

        assertEquals(0, failures.size(),
                () -> "Some unlisted code points folded unexpectedly:\n"
                        + String.join("\n", failures));
    }

    @ParameterizedTest(name = "CaseFold \"{0}\" → \"{1}\"")
    @MethodSource("caseFoldTestCases")
    void testIndividualCaseFolding(String input, String expected) {
        assertEquals(expected, foldCase(input));
    }

    static Stream<Arguments> caseFoldTestCases() {
        return Stream.of(
                // ASCII simple cases
                Arguments.of("ABC", "abc"),
                Arguments.of("already", "already"),
                Arguments.of("MiXeD123", "mixed123"),
                // --- Latin-1 to non-Latin-1 fold ---
                Arguments.of("aBc\u00B5Efg", "abc\u03BCefg"), // "µ" → "μ"
                Arguments.of("test\u00B5\ud801\udc00X", "test\u03bc\ud801\udc28x"),
                // German Eszett
                Arguments.of("Stra\u00DFe", "strasse"), // "Straße"
                Arguments.of("\u1E9E", "ss"), // "ẞ"  capital sharp S
                // Turkish dotted I / dotless i
                Arguments.of("I", "i"),
                Arguments.of("\u0130", "i\u0307"), // capital dotted I → "i + dot above"
                Arguments.of("\u0069\u0307", "i\u0307"), // small i + dot above remains
                Arguments.of("\u0131", "\u0131"), // "ı" (dotless i stays dotless)

                // Greek special cases ---
                Arguments.of("\u039F\u03A3", "\u03BF\u03C3"), // "ΟΣ" → "οσ"  final sigma always folds to normal sigma
                Arguments.of("\u1F88", "\u1F00\u03B9"), // "ᾈ" → "ἀι"    Alpha with psili + ypogegrammeni
                Arguments.of("\u039C\u03AC\u03CA\u03BF\u03C2", "\u03BC\u03AC\u03CA\u03BF\u03C3"), // "Μάϊος" → "μάϊοσ"
                Arguments.of("\u1F08", "\u1F00"), //  Ἀ (Capital Alpha with psili) → ἀ

                // Supplementary Plane characters
                Arguments.of("\uD801\uDC00", "\uD801\uDC28"), // Deseret Capital Letter Long I → Small
                Arguments.of("\uD801\uDC01", "\uD801\uDC29"), // Deseret Capital Letter Long E → Small

                // Supplementary inside ASCII
                Arguments.of("abc\uD801\uDC00def", "abc\uD801\uDC28def"),
                // Ligatures and compatibility folds
                Arguments.of("\uFB00", "ff"), // ﬀ → ff
                Arguments.of("\uFB03", "ffi"), // ﬃ → ffi
                Arguments.of("\u212A", "k"), // Kelvin sign → k

                Arguments.of("abc\uFB00def", "abcffdef"), // ﬀ → ff
                Arguments.of("abc\uFB03def", "abcffidef"), // ﬃ → ffi
                Arguments.of("abc\u212Adef", "abckdef"), // Kelvin sign → k

                // --- Fullwidth ---
                Arguments.of("\uFF21\uFF22\uFF23", "\uFF41\uFF42\uFF43"), // "ＡＢＣ" → "ａｂｃ"

                // --- Armenian ---
                Arguments.of("\u0531", "\u0561"), // "Ա" → "ա"

                // --- Cherokee ---
                Arguments.of("\u13A0", "\u13A0"), // Capital Cherokee A folds to itself
                Arguments.of("\uAB70", "\u13A0") // Small Cherokee A folds Capital Cherokee A
        );
    }

    static Stream<Arguments> caseFoldEqualProvider() {
        return Stream.of(
                Arguments.of("abc", "ABC"),
                Arguments.of("aBcDe", "AbCdE"),
                Arguments.of("\u00C0\u00E7", "\u00E0\u00C7"), // Àç vs àÇ
                Arguments.of("straße", "STRASSE"), // ß → ss
                Arguments.of("\uD83C\uDDE6", "\uD83C\uDDE6"), // 🇦 vs 🇦
                Arguments.of("\u1E9E", "ss"), // ẞ (capital sharp S)
                Arguments.of("\u03A3", "\u03C3"), // Σ vs σ (Greek Sigma)
                Arguments.of("\u03C3", "\u03C2"), // σ vs ς (Greek sigma/final sigma)
                Arguments.of("\u212B", "\u00E5"), // Å (Angstrom sign) vs å
                Arguments.of("\uFB00", "ff"), // ﬀ (ligature)
                Arguments.of("\u01C5", "\u01C5"), // ǅ (Latin capital D with small z with caron)
                Arguments.of("Caf\u00E9", "CAF\u00C9"), // Café vs CAFÉ
                Arguments.of("\u03BA\u03B1\u03BB\u03B7\u03BC\u03AD\u03C1\u03B1", "\u039A\u0391\u039B\u0397\u039C\u0388\u03A1\u0391"), // καλημέρα vs ΚΑΛΗΜΕΡΑ
                Arguments.of("\u4E2D\u56FD", "\u4E2D\u56FD"), // 中国
                Arguments.of("\u03B1", "\u0391"), // α vs Α (Greek alpha)
                Arguments.of("\u212B", "\u00C5"), // Å vs Å
                // from StringCompareToIgnoreCase
                Arguments.of("\u0100\u0102\u0104\u0106\u0108", "\u0100\u0102\u0104\u0106\u0109"), // ĀĂĄĆĈ vs ĀĂĄĆĉ
                Arguments.of("\u0101\u0103\u0105\u0107\u0109", "\u0100\u0102\u0104\u0106\u0109"), // āăąćĉ vs ĀĂĄĆĉ
                Arguments.of("\ud801\udc00\ud801\udc01\ud801\udc02\ud801\udc03\ud801\udc04",
                        "\ud801\udc00\ud801\udc01\ud801\udc02\ud801\udc03\ud801\udc2c"), // 𐐀𐐁𐐂𐐃𐐄 vs 𐐀𐐁𐐂𐐃𐐬
                Arguments.of("\ud801\udc28\ud801\udc29\ud801\udc2a\ud801\udc2b\ud801\udc2c",
                        "\ud801\udc00\ud801\udc01\ud801\udc02\ud801\udc03\ud801\udc2c") // 𐐨𐐩𐐪𐐫𐐬 vs 𐐀𐐁𐐂𐐃𐐬
        );
    }

    @ParameterizedTest
    @MethodSource("caseFoldEqualProvider")
    void testcompareToFoldCaseEquals(String s1, String s2) {
        assertEquals(0, s1.compareToFoldCase(s2));
        assertEquals(0, s2.compareToFoldCase(s1));
        assertEquals(true, s1.equalsFoldCase(s2));
        assertEquals(true, s2.equalsFoldCase(s1));
        assertEquals(foldCase(s1), foldCase(s2));
    }

    static Stream<Arguments> caseFoldOrderingProvider() {
        return Stream.of(
                Arguments.of("asa", "aß", -1), // ß → ss → "asa" < "ass"
                Arguments.of("aß", "asa", +1),
                Arguments.of("a\u00DF", "ass", 0), // aß vs ass
                Arguments.of("\uFB03", "ffi", 0), // ﬃ (ligature)
                Arguments.of("\u00C5", "Z", 1), // Å vs Z
                Arguments.of("A", "\u00C0", -1), // A vs À
                Arguments.of("\u03A9", "\u03C9", 0), // Ω vs ω
                Arguments.of("\u03C2", "\u03C3", 0), // ς vs σ
                Arguments.of("\uD835\uDD23", "R", 1), // 𝔯 (fraktur r) vs R
                Arguments.of("\uFF26", "E", 1), // Ｆ (full-width F) vs E
                Arguments.of("\u00C9clair", "Eclair", 1), // Éclair vs Eclair
                Arguments.of("\u03bc\u00df", "\u00b5s", 1),
                Arguments.of("\u00b5s", "\u03bc\u00df", -1)
        );
    }

    @ParameterizedTest
    @MethodSource("caseFoldOrderingProvider")
    void testcompareToFoldCaseOrdering(String s1, String s2, int expectedSign) {
        int cmp = s1.compareToFoldCase(s2);
        assertEquals(expectedSign, Integer.signum(cmp));
    }

    static Stream<Arguments> roundTripProvider() {
        return Stream.of(
                Arguments.of("abc"),
                Arguments.of("ABC"),
                Arguments.of("straße"),
                Arguments.of("Àç"),
                Arguments.of("aß"),
                Arguments.of("\uFB02uff"), // ﬂuff (ligature in "fluff")
                Arguments.of("\u00C9COLE") // ÉCOLE
        );
    }

    @ParameterizedTest
    @MethodSource("roundTripProvider")
    void testCaseFoldRoundTrip(String s) {
        String folded = foldCase(s);
        assertEquals(0, s.compareToFoldCase(folded));
        assertEquals(0, folded.compareToFoldCase(s));
        assertEquals(true, s.equalsFoldCase(folded));
        assertEquals(true, folded.equalsFoldCase(s));
    }

    // helper to test the integrity of folding mapping
    private static int[] longToFolding(long value) {
        int len = (int) (value >>> 48);
        if (len == 0) {
            return new int[]{(int) (value & 0xFFFFF)};
        } else {
            var folding = new int[len];
            for (int i = 0; i < len; i++) {
                folding[i] = (int) (value & 0xFFFF);
                value >>= 16;
            }
            return folding;
        }
    }

    private static String foldCase(String s) {
        int first;
        int len = s.length();
        int cpCnt = 1;
        for (first = 0; first < len; first += cpCnt) {
            int cp = s.codePointAt(first);
            if (CaseFolding.isDefined(cp)) {
                break;
            }
            cpCnt = Character.charCount(cp);
        }
        if (first == len) {
            return s;
        }
        StringBuilder sb = new StringBuilder(len);
        sb.append(s, 0, first);
        for (int i = first; i < len; i += cpCnt) {
            int cp = s.codePointAt(i);
            int[] folded = longToFolding(CaseFolding.fold(cp));
            for (int f : folded) {
                sb.appendCodePoint(f);
            }
            cpCnt = Character.charCount(cp);
        }
        return sb.toString();
    }
}
