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
 * @bug 6941948
 * @summary Make sure that EASTERN_ARABIC works with the enum interface.
 */

import java.awt.font.NumericShaper;
import java.util.EnumSet;
import static java.awt.font.NumericShaper.*;

public class EasternArabicTest {
    static NumericShaper ns_old, ns_new;
    static boolean err = false;

    static String[][] testData = {
        // Arabic "October 10"
        {"أكتوبر 10",
         "أكتوبر ۱۰"}, // EASTERN_ARABIC digits

        // Tamil "Year 2009"
        {"ஆண்டு 2009",
         "ஆண்டு ௨௦௦௯"},
        // "௨00௯ is returned by pre-JDK7 because Tamil zero was not
        //  included in Unicode 4.0.0.

        // Ethiopic "Syllable<HA> 2009"
        {"ሀ 2009",
         "ሀ ፪00፱"},
        // Ethiopic zero doesn't exist even in Unicode 5.1.0.
    };

    public static void main(String[] args) {
        ns_old = getContextualShaper(TAMIL|ETHIOPIC|EASTERN_ARABIC|ARABIC|THAI|LAO,
                                     EUROPEAN);
        ns_new = getContextualShaper(EnumSet.of(Range.THAI,
                                                Range.TAMIL,
                                                Range.ETHIOPIC,
                                                Range.EASTERN_ARABIC,
                                                Range.ARABIC,
                                                Range.LAO),
                                     Range.EUROPEAN);


        StringBuilder cData = new StringBuilder();
        StringBuilder cExpected = new StringBuilder();
        for (int i = 0; i < testData.length; i++) {
            String data = testData[i][0];
            String expected = testData[i][1];
            test(data, expected);
            cData.append(data).append(' ');
            cExpected.append(expected).append(' ');
        }
        test(cData.toString(), cExpected.toString());

        if (err) {
            throw new RuntimeException("shape() returned unexpected value.");
        }
    }

    private static void test(String data, String expected) {
        char[] text = data.toCharArray();
        ns_old.shape(text, 0, text.length);
        String got = new String(text);

        if (!expected.equals(got)) {
            err = true;
            System.err.println("Error with traditional range.");
            System.err.println("  text = " + data);
            System.err.println("  got = " + got);
            System.err.println("  expected = " + expected);
        } else {
            System.err.println("OK with traditional range.");
            System.err.println("  text = " + data);
            System.err.println("  got = " + got);
            System.err.println("  expected = " + expected);
        }

        text = data.toCharArray();
        ns_new.shape(text, 0, text.length);
        got = new String(text);

        if (!expected.equals(got)) {
            err = true;
            System.err.println("Error with new Enum range.");
            System.err.println("  text = " + data);
            System.err.println("  got = " + got);
            System.err.println("  expected = " + expected);
        } else {
            System.err.println("OK with new Enum range.");
            System.err.println("  text = " + data);
            System.err.println("  got = " + got);
            System.err.println("  expected = " + expected);
        }
    }
}
