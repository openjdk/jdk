/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6842557 6943963
 * @summary confirm that shaping works as expected. (Mainly for new characters which were added in Unicode 5)
 * used where appropriate.
 */

import java.awt.font.NumericShaper;
import java.util.EnumSet;
import static java.awt.font.NumericShaper.*;

public class ShapingTest {

    private static boolean err = false;

    public static void main(String[] args) {
        test6842557();
        test6943963();

        if (err) {
            throw new RuntimeException("shape() returned unexpected value.");
        }
    }

    private static void test6842557() {
        NumericShaper ns_old = getContextualShaper(ARABIC | TAMIL | ETHIOPIC,
                                   EUROPEAN);
        NumericShaper ns_new = getContextualShaper(EnumSet.of(
                                   Range.ARABIC, Range.TAMIL, Range.ETHIOPIC),
                                   Range.EUROPEAN);

        String[][] data = {
           // Arabic "October 10"
          {"\u0623\u0643\u062a\u0648\u0628\u0631 10",
           "\u0623\u0643\u062a\u0648\u0628\u0631 \u0661\u0660"},

           // Tamil "Year 2009"
          {"\u0b86\u0ba3\u0bcd\u0b9f\u0bc1 2009",
           "\u0b86\u0ba3\u0bcd\u0b9f\u0bc1 \u0be8\u0be6\u0be6\u0bef"},
           // "\u0be800\u0bef is returned by pre-JDK7 because Tamil zero was not
           //  included in Unicode 4.0.0.

           // Ethiopic "Syllable<HA> 2009"
          {"\u1200 2009",
           "\u1200 \u136a00\u1371"},
           // Ethiopic zero doesn't exist even in Unicode 5.1.0.
        };

        for (int i = 0; i < data.length; i++) {
            checkResult("ARABIC | TAMIL | ETHIOPIC",
                        ns_old, data[i][0], data[i][1]);

            checkResult("Range.ARABIC, Range.TAMIL, Range.ETHIOPIC",
                        ns_new, data[i][0], data[i][1]);
        }
    }

    private static void test6943963() {
        // Needed to reproduce this bug.
        NumericShaper ns_dummy = getContextualShaper(ARABIC | TAMIL | ETHIOPIC,
                                   EUROPEAN);
        char[] c = "\u1200 1".toCharArray();
        ns_dummy.shape(c, 0, c.length);


        String given = "\u0627\u0628 456";
        String expected_ARABIC = "\u0627\u0628 \u0664\u0665\u0666";
        String expected_EASTERN_ARABIC = "\u0627\u0628 \u06f4\u06f5\u06f6";

        NumericShaper ns = getContextualShaper(ARABIC);
        checkResult("ARABIC", ns, given, expected_ARABIC);

        ns = getContextualShaper(EnumSet.of(Range.ARABIC));
        checkResult("Range.ARABIC", ns, given, expected_ARABIC);

        ns = getContextualShaper(EASTERN_ARABIC);
        checkResult("EASTERN_ARABIC", ns, given, expected_EASTERN_ARABIC);

        ns = getContextualShaper(EnumSet.of(Range.EASTERN_ARABIC));
        checkResult("Range.EASTERN_ARABIC", ns, given, expected_EASTERN_ARABIC);

        ns = getContextualShaper(ARABIC | EASTERN_ARABIC);
        checkResult("ARABIC | EASTERN_ARABIC", ns, given, expected_EASTERN_ARABIC);

        ns = getContextualShaper(EnumSet.of(Range.ARABIC, Range.EASTERN_ARABIC));
        checkResult("Range.ARABIC, Range.EASTERN_ARABIC", ns, given, expected_EASTERN_ARABIC);
    }

    private static void checkResult(String ranges, NumericShaper ns,
                                    String given, String expected) {
        char[] text = given.toCharArray();
        ns.shape(text, 0, text.length);
        String got = new String(text);

        if (!expected.equals(got)) {
            err = true;
            System.err.println("Error with range(s) <" + ranges + ">.");
            System.err.println("  text     = " + given);
            System.err.println("  got      = " + got);
            System.err.println("  expected = " + expected);
        } else {
            System.out.println("OK with range(s) <" + ranges + ">.");
            System.out.println("  text     = " + given);
            System.out.println("  got      = " + got);
            System.out.println("  expected = " + expected);
        }
    }

}
