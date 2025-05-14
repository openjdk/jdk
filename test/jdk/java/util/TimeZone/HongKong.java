/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4487276 8008577 8174269
 * @modules jdk.localedata
 * @summary Verify that Hong Kong locale uses traditional Chinese names.
 * @run main/othervm HongKong
 */

import java.util.Locale;
import java.util.TimeZone;

public class HongKong {
    public static void main(String[] args) {
        Locale reservedLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("zh", "HK"));
            checkCountry(Locale.GERMANY, "德國");
            checkCountry(Locale.FRANCE, "法國");
            checkCountry(Locale.ITALY, "意大利");
            checkTimeZone("Asia/Shanghai",
                            "中國標準時間");
        } finally {
            // restore the reserved locale
            Locale.setDefault(reservedLocale);
        }
    }

    private static void checkCountry(Locale country, String expected) {
        String actual = country.getDisplayCountry();
        if (!expected.equals(actual)) {
            throw new RuntimeException("Failed. actual: %s, expected: %s"
                    .formatted(actual, expected));
        }
    }

    private static void checkTimeZone(String timeZoneID, String expected) {
        TimeZone timeZone = TimeZone.getTimeZone(timeZoneID);
        String actual = timeZone.getDisplayName();
        if (!expected.equals(actual)) {
            throw new RuntimeException("Failed. actual: %s, expected: %s"
                .formatted(actual, expected));
        }
    }
}
