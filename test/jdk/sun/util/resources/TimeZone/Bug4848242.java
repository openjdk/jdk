/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *@test
 *@bug 4848242 8342886
 *@summary Verifies that sampled European locales use consistent short time zone names.
 *         Originally assumed all European locales had the same short names,
 *         but due to changes in time zone data and locale handling, that is no longer guaranteed.
 *         This test now verifies that a representative sample of locales (e.g., fr, it)
 *         still use the same short names (CET/CEST).
 */

import java.util.Locale;
import java.util.TimeZone;
import java.text.DateFormatSymbols;

public class Bug4848242 {

    public static void main(String[] args) {
        getTzInfo("es", "ES");
        getTzInfo("fr", "FR");
        getTzInfo("it", "IT");
        getTzInfo("sv", "SV");
    }

    static void getTzInfo(String langName, String locName)
    {
        Locale tzLocale = Locale.of(langName, locName);
        TimeZone euroTz = TimeZone.getTimeZone("MET");
        System.out.println("Locale is " + langName + "_" + locName);

        if ( euroTz.getID().equalsIgnoreCase("GMT") ) {
            // if we don't have a timezone and default back to GMT
            throw new RuntimeException("Error: no time zone found");
        }

        // get the timezone info
        System.out.println(euroTz.getDisplayName(false, TimeZone.SHORT, tzLocale));
        if (!euroTz.getDisplayName(false, TimeZone.SHORT, tzLocale).equals("CET"))
            throw new RuntimeException("Timezone name is incorrect (should be CET)\n");
        System.out.println(euroTz.getDisplayName(false, TimeZone.LONG, tzLocale));

        System.out.println(euroTz.getDisplayName(true, TimeZone.SHORT, tzLocale));
        if (!euroTz.getDisplayName(true, TimeZone.SHORT, tzLocale).equals("CEST"))
            throw new RuntimeException("Summer timezone name is incorrect (should be CEST)\n");
        System.out.println(euroTz.getDisplayName(true, TimeZone.LONG, tzLocale) + "\n");

    }

}
