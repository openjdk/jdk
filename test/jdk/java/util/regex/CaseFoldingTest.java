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
 * @summary tests RegExp unicode case-insensitive match (?ui)
 * @bug 8360459
 * @library /lib/testlibrary/java/lang
 * @run junit CaseFoldingTest
 */

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CaseFoldingTest {

    @Test
    void testUnicodeCaseInsensitiveMatch() throws Throwable {
        var testAll = true;   // true to test all codepoints defined in CaseFolding.txt
        var verbose = true;   // true to display all codepoints being tested
        var filter = "^.*; [CTS]; .*$";  // update C,T,S to test different type
        var excluded = Set.of(
            // these 'S' characters failed for known reason. they don't map to their
            // folding form with toUpperCase or toLowerCase, only map with case-folding.
            // exclude them for now.
            0x1fd3,  // 1FD3 [lo: 1fd3, up: 1fd3]  0390 [lo: 0390, up: 0390]
            0x1fe3,  // 1FE3 [lo: 1fe3, up: 1fe3]  03B0 [lo: 03b0, up: 03b0]
            0xfb05   // FB05 [lo: fb05, up: fb05]  FB06 [lo: fb06, up: fb06]
        );

        var results = Files.lines(UCDFiles.CASEFOLDING)
            .filter(line -> !line.startsWith("#") && line.matches(filter))
            .map(line -> {
                var strs = line.split("; ");
                return new String[] {strs[0], strs[1], strs[2]};
            })
            .filter(cps -> {
                var cp1 = Integer.parseInt(cps[0], 16);
                var cp2 = Integer.parseInt(cps[2], 16);
                if (excluded.contains(cp1))
                    return false;
                if (testAll) {
                    return true;
                }
                // the folding codepoint doesn't map back to the original codepoint.
                return Character.toUpperCase(cp2) != cp1 && Character.toLowerCase(cp2) != cp1;
            })
            .flatMap(cps -> {
                // test slice, single & range
                var cp = Integer.parseInt(cps[0], 16);
                var folding = Integer.parseInt(cps[2], 16);
                var errors = testCaseFolding(cp, folding);
                if (verbose)
                    System.out.format(" [%s] %s [lo: %04x, up: %04x]  %s [lo: %04x, up: %04x]\n",
                        cps[1],
                        cps[0],
                        Character.toLowerCase(cp),
                        Character.toUpperCase(cp),
                        cps[2],
                        Character.toLowerCase(folding),
                        Character.toUpperCase(folding)
                    );
                errors.forEach(error -> System.out.print(error));
                return errors.stream();
            })
            .collect(Collectors.toList());
        assertEquals(results.size(), 0);
    }

    private static ArrayList<String> testCaseFolding(int cp, int folding) {
        ArrayList<String> errors = new ArrayList<>();
        testCaseFolding0(cp, folding, errors, "s-t");
        testCaseFolding0(folding, cp, errors, "t-s");
        // test all uppercase, lowercase combinations
        var up = Character.toUpperCase(cp);
        var lo = Character.toLowerCase(cp);
        var folding_up = Character.toUpperCase(folding);  // folding should be normally lowercase
        if (up != cp) {
            testCaseFolding0(up, folding, errors, "s(u)-t");
            testCaseFolding0(folding, up, errors, "t-s(u)");
            if (folding_up != folding) {
                testCaseFolding0(up, folding_up, errors, "s(u)-t(u)");
                testCaseFolding0(folding_up, up, errors, "t(u)-s(u)");
            }
        }
        if (lo != cp) {
            testCaseFolding0(lo, folding, errors, "s(l)-t");
            testCaseFolding0(folding, lo, errors, "t-s(l)");
            if (folding_up != folding) {
                testCaseFolding0(lo, folding_up, errors, "s(l)-t(u)");
                testCaseFolding0(folding_up, lo, errors, "t(u)-s(l)");
            }
        }
        return errors;
    }

    private static void testCaseFolding0(int cp, int folding, ArrayList<String> errors, String type) {
        var cp_str = Character.isSupplementaryCodePoint(cp)
            ? String.format("\\u%04x\\u%04x", (int)Character.highSurrogate(cp), (int)Character.lowSurrogate(cp))
            : String.format("\\u%04x", cp);

        var t = new String(Character.toChars(folding));
        var p = String.format("(?iu)%s", cp_str);

        if (Pattern.compile(p).matcher(t).matches() == false) {
            errors.add(String.format("     [FAILED] slice:  %-20s  t: u+%04x  (%s)\n", p, folding, type));
        }

        p = String.format("(?iu)[%s]", cp_str);
        if (Pattern.compile(p).matcher(t).matches() == false) {
            errors.add(String.format("     [FAILED] single: %-20s  t: u+%04x  (%s)\n", p, folding, type));
        }

        p = String.format("(?iu)[%s-%s]", cp_str, cp_str);
        if (Pattern.compile(p).matcher(t).matches() == false) {
            errors.add(String.format("     [FAILED] range:  %-20s  t: u+%04x  (%s)\n", p, folding, type));
        }

        // small range
        var end_cp = cp + 16;
        var end_cp_str = Character.isSupplementaryCodePoint(end_cp)
                ? String.format("\\u%04x\\u%04x", (int)Character.highSurrogate(end_cp), (int)Character.lowSurrogate(end_cp))
                : String.format("\\u%04x", end_cp);
        p = String.format("(?iu)[%s-%s]", cp_str, end_cp_str);
        if (Pattern.compile(p).matcher(t).matches() == false) {
            errors.add(String.format("     [FAILED] range:  %-20s  t: u+%04x  (%s)\n", p, folding, type));
        }

        end_cp = cp + 128;  // bigger than the expanded_casefolding_map.
        end_cp_str = Character.isSupplementaryCodePoint(end_cp)
                ? String.format("\\u%04x\\u%04x", (int)Character.highSurrogate(end_cp), (int)Character.lowSurrogate(end_cp))
                : String.format("\\u%04x", end_cp);
        p = String.format("(?iu)[%s-%s]", cp_str, end_cp_str);
        if (Pattern.compile(p).matcher(t).matches() == false) {
            errors.add(String.format("     [FAILED] range:  %-20s  t: u+%04x  (%s)\n", p, folding, type));
        }
    }
}
