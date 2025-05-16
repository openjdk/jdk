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
 * @bug 6317929 6409419 8008577 8174269
 * @modules jdk.localedata
 * @summary Test case for tzdata2005m support for 9 locales
 * @run main Bug6317929
 */

import java.util.Locale;
import java.util.TimeZone;

public class Bug6317929 {
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
        Locale tzLocale;

        TimeZone Coral_Harbour = TimeZone.getTimeZone("America/Coral_Harbour");
        tzLocale = locales2Test[0];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Eastern Standard Time"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"Eastern Standard Time\"");
        tzLocale = locales2Test[1];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Nordamerikanische Ostküsten-Normalzeit"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"Nordamerikanische Ostküsten-Normalzeit\"");
        tzLocale = locales2Test[2];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("hora estándar oriental"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"hora estándar oriental\"");
        tzLocale = locales2Test[3];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("heure normale de l’Est nord-américain"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"heure normale de l’Est nord-américain\"");
        tzLocale = locales2Test[4];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Ora standard orientale USA"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"Ora standard orientale USA\"");
        tzLocale = locales2Test[5];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("アメリカ東部標準時"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"アメリカ東部標準時\"");
        tzLocale = locales2Test[6];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("미 동부 표준시"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"미 동부 표준시\"");
        tzLocale = locales2Test[7];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("östnordamerikansk normaltid"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"östnordamerikansk normaltid\"");
        tzLocale = locales2Test[8];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("北美东部标准时间"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"北美东部标准时间\"");
        tzLocale = locales2Test[9];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("東部標準時間"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"東部標準時間\"");

        TimeZone Currie = TimeZone.getTimeZone("Australia/Currie");
        tzLocale = locales2Test[0];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Australian Eastern Standard Time"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"Australian Eastern Standard Time\"");
        tzLocale = locales2Test[1];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Ostaustralische Normalzeit"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"Ostaustralische Normalzeit\"");
        tzLocale = locales2Test[2];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("hora estándar de Australia oriental"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"hora estándar de Australia oriental\"");
        tzLocale = locales2Test[3];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("heure normale de l’Est de l’Australie"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"heure normale de l’Est de l’Australie\"");
        tzLocale = locales2Test[4];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Ora standard dell’Australia orientale"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"Ora standard dell’Australia orientale\"");
        tzLocale = locales2Test[5];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("オーストラリア東部標準時"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"オーストラリア東部標準時\"");
        tzLocale = locales2Test[6];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("오스트레일리아 동부 표준시"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"오스트레일리아 동부 표준시\"");
        tzLocale = locales2Test[7];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("östaustralisk normaltid"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"östaustralisk normaltid\"");
        tzLocale = locales2Test[8];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("澳大利亚东部标准时间"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"澳大利亚东部标准时间\"");
        tzLocale = locales2Test[9];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("澳洲東部標準時間"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"澳洲東部標準時間\"");
   }
}
