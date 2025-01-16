/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6377794 8174269
 * @modules jdk.localedata
 * @summary Test case for tzdata2005r support for 9 locales
 * @run main Bug6377794
 */

import java.util.Locale;
import java.util.TimeZone;

public class Bug6377794 {
    static Locale[] locales2Test = new Locale[] {
        Locale.ENGLISH,
        Locale.GERMAN,
        Locale.of("es"),
        Locale.FRENCH,
        Locale.ITALIAN,
        Locale.JAPANESE,
        Locale.KOREAN,
        Locale.of("sv"),
        Locale.SIMPLIFIED_CHINESE,
        Locale.TRADITIONAL_CHINESE
    };

    public static void main(String[] args) {
        // As of CLDR 44, "SystemV/YST9" is replaced by "Pacific/Gambier" in supplementalMetadata.xml
        TimeZone SystemVYST9 = TimeZone.getTimeZone("SystemV/YST9");
        Locale tzLocale;
        for (int i = 0; i < locales2Test.length; i++) {
            tzLocale = locales2Test[i];
            if (i == 3) {
                // French
                if (!SystemVYST9.getDisplayName(false, TimeZone.SHORT, tzLocale).equals
                        ("UTC\u221209:00"))
                    throw new RuntimeException("\n" + tzLocale + ": SHORT, " +
                            "non-daylight saving name for " +
                            "SystemV/YST9 should be \"UTC\u221209:00\"");
            } else if (i == 7) {
                // Swedish
                if (!SystemVYST9.getDisplayName(false, TimeZone.SHORT, tzLocale).equals
                        ("GMT\u221209:00"))
                    throw new RuntimeException("\n" + tzLocale + ": SHORT, " +
                            "non-daylight saving name for " +
                            "SystemV/YST9 should be \"GMT\u221209:00\"");
            } else {
                if (!SystemVYST9.getDisplayName(false, TimeZone.SHORT, tzLocale).equals
                        ("GMT-09:00"))
                    throw new RuntimeException("\n" + tzLocale + ": SHORT, " +
                            "non-daylight saving name for " +
                            "SystemV/YST9 should be \"GMT-09:00\"");
            }
        }

/*
 * For "SystemV/PST8", testing TimeZone.SHORT would return the same value
 * before and after the fix. Therefore, the regression test was changed to test
 * TimeZone.LONG instead.
 */

            // As of CLDR 44, "SystemV/PST8" is replaced by "Pacific/Pitcairn"
            // in supplementalMetadata.xml
            TimeZone SystemVPST8 = TimeZone.getTimeZone("SystemV/PST8");
            tzLocale = locales2Test[0];
            if (!SystemVPST8.getDisplayName(false, TimeZone.LONG, tzLocale).equals
               ("Pitcairn Time"))
                throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                           "non-daylight saving name for " +
                                           "SystemV/PST8 should be " +
                                           "\"Pitcairn Time\"");
            tzLocale = locales2Test[1];
            if (!SystemVPST8.getDisplayName(false, TimeZone.LONG, tzLocale).equals
               ("Pitcairninseln-Zeit"))
                throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                           "non-daylight saving name for " +
                                           "SystemV/PST8 should be " +
                                           "\"Pitcairninseln-Zeit\"");
            tzLocale = locales2Test[2];
            if (!SystemVPST8.getDisplayName(false, TimeZone.LONG, tzLocale).equals
               ("hora de Pitcairn"))
                throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                           "non-daylight saving name for " +
                                           "SystemV/PST8 should be " +
                                           "\"hora de Pitcairn\"");
            tzLocale = locales2Test[3];
            if (!SystemVPST8.getDisplayName(false, TimeZone.LONG, tzLocale).equals
               ("heure des \u00eeles Pitcairn"))
                throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                           "non-daylight saving name for " +
                                           "SystemV/PST8 should be " +
                                           "\"heure des \u00eeles Pitcairn\"");
            tzLocale = locales2Test[4];
            if (!SystemVPST8.getDisplayName(false, TimeZone.LONG, tzLocale).equals
               ("Ora delle Pitcairn"))
                throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                           "non-daylight saving name for " +
                                           "SystemV/PST8 should be " +
                                           "\"Ora delle Pitcairn\"");
            tzLocale = locales2Test[5];
            if (!SystemVPST8.getDisplayName(false, TimeZone.LONG, tzLocale).equals
               ("\u30d4\u30c8\u30b1\u30a2\u30f3\u6642\u9593"))
                throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                           "non-daylight saving name for " +
                                           "SystemV/PST8 should be " +
                                           "\"\u30d4\u30c8\u30b1\u30a2\u30f3\u6642\u9593\"");
            tzLocale = locales2Test[6];
            if (!SystemVPST8.getDisplayName(false, TimeZone.LONG, tzLocale).equals
               ("\ud54f\ucf00\uc5b8 \uc2dc\uac04"))
                throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                           "non-daylight saving name for " +
                                           "SystemV/PST8 should be " +
                                           "\"\ud54f\ucf00\uc5b8 \uc2dc\uac04\"");
            tzLocale = locales2Test[7];
            if (!SystemVPST8.getDisplayName(false, TimeZone.LONG, tzLocale).equals
               ("Pitcairntid"))
                throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                           "non-daylight saving name for " +
                                           "SystemV/PST8 should be " +
                                           "\"Pitcairntid\"");
            tzLocale = locales2Test[8];
            if (!SystemVPST8.getDisplayName(false, TimeZone.LONG, tzLocale).equals
               ("\u76ae\u7279\u51ef\u6069\u65f6\u95f4"))
                throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                           "non-daylight saving name for " +
                                           "SystemV/PST8 should be " +
                                           "\"\u76ae\u7279\u51ef\u6069\u65f6\u95f4\"");
            tzLocale = locales2Test[9];
            if (!SystemVPST8.getDisplayName(false, TimeZone.LONG, tzLocale).equals
               ("\u76ae\u7279\u80af\u6642\u9593"))
                throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                           "non-daylight saving name for " +
                                           "SystemV/PST8 should be " +
                                           "\"\u76ae\u7279\u80af\u6642\u9593\"");
   }
}
