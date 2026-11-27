/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8392223
 * @summary A time zone display name must not depend on which locale was
 *          looked up first in the process
 * @library /test/lib
 * @run main TimeZoneNameOrderTest
 */
import java.util.Locale;
import java.util.TimeZone;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TimeZoneNameOrderTest {

    // zone, target locale, parent locale looked up first, daylight, style.
    // Before the fix, names derived for the parent locale were written into
    // its cached array and inherited by the child locale on a later lookup.
    private static final String[][] CASES = {
        {"Africa/Casablanca",  "fr-FR", "und", "false", "SHORT"},
        {"Atlantic/St_Helena", "en-IN", "en",  "true",  "LONG"},
    };

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            child(args);
            return;
        }
        boolean failed = false;
        for (String[] c : CASES) {
            String direct = run(c, false);
            String parentFirst = run(c, true);
            System.out.printf("%s %s %s %s: direct=%s parentFirst=%s%n",
                    c[0], c[1], c[3].equals("true") ? "dst" : "std", c[4],
                    direct, parentFirst);
            if (!direct.equals(parentFirst)) {
                failed = true;
            }
        }
        if (failed) {
            throw new RuntimeException(
                "display name depends on the order of locale lookups");
        }
    }

    private static String run(String[] c, boolean parentFirst)
            throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "TimeZoneNameOrderTest",
                c[0], c[1], parentFirst ? c[2] : "-", c[3], c[4]);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldHaveExitValue(0);
        return output.getStdout().trim();
    }

    private static void child(String[] a) {
        TimeZone tz = TimeZone.getTimeZone(a[0]);
        Locale target = Locale.forLanguageTag(a[1]);
        boolean daylight = Boolean.parseBoolean(a[3]);
        int style = a[4].equals("LONG") ? TimeZone.LONG : TimeZone.SHORT;
        if (!a[2].equals("-")) {
            tz.getDisplayName(daylight, style, Locale.forLanguageTag(a[2]));
        }
        System.out.println(tz.getDisplayName(daylight, style, target));
    }
}
