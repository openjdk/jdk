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
 * @bug 6271396 8008577 8174269
 * @modules jdk.localedata
 * @summary Test case for verifying typo of timezone display name Australia/Lord_Howe
 * @run main Bug6271396
 */

import java.util.Locale;
import java.util.TimeZone;

public class Bug6271396 {

    public static void main(String[] args) {

        TimeZone Lord_Howe = TimeZone.getTimeZone("Australia/Lord_Howe");
        Locale tzLocale = Locale.FRENCH;

        if (!Lord_Howe.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("heure normale de Lord Howe"))
             throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                        "non-daylight saving name for " +
                                        "Australia/Lord_Howe should be " +
                                        "\"Heure standard de Lord Howe\"");
        if (!Lord_Howe.getDisplayName(true, TimeZone.LONG, tzLocale).equals
           ("heure d’été de Lord Howe"))
             throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                        "daylight saving name for " +
                                        "Australia/Lord_Howe should be " +
                                        "\"Heure d'été de Lord Howe\"");

        tzLocale = Locale.TRADITIONAL_CHINESE;
        if (!Lord_Howe.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("豪勳爵島標準時間"))
             throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                        "non-daylight saving name for " +
                                        "Australia/Lord_Howe should be " +
                                        "\"豪勳爵島" +
                                        "標準時間\"");
        if (!Lord_Howe.getDisplayName(true, TimeZone.LONG, tzLocale).equals
           ("豪勳爵島夏令時間"))
             throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                        "daylight saving name for " +
                                        "Australia/Lord_Howe should be " +
                                        "\"豪勳爵島" +
                                        "夏令時間\"");
   }
}
