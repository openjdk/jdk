/*
 * Copyright (c) 1997, 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

/*
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 */

package sun.text.resources;

import java.util.ListResourceBundle;

public class FormatData_ar extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "\u064a\u0646\u0627\u064a\u0631", // january
                    "\u0641\u0628\u0631\u0627\u064a\u0631", // february
                    "\u0645\u0627\u0631\u0633", // march
                    "\u0623\u0628\u0631\u064a\u0644", // april
                    "\u0645\u0627\u064a\u0648", // may
                    "\u064a\u0648\u0646\u064a\u0648", // june
                    "\u064a\u0648\u0644\u064a\u0648", // july
                    "\u0623\u063a\u0633\u0637\u0633", // august
                    "\u0633\u0628\u062a\u0645\u0628\u0631", // september
                    "\u0623\u0643\u062a\u0648\u0628\u0631", // october
                    "\u0646\u0648\u0641\u0645\u0628\u0631", // november
                    "\u062f\u064a\u0633\u0645\u0628\u0631", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "\u064a\u0646\u0627", // abb january
                    "\u0641\u0628\u0631", // abb february
                    "\u0645\u0627\u0631", // abb march
                    "\u0623\u0628\u0631", // abb april
                    "\u0645\u0627\u064a", // abb may
                    "\u064a\u0648\u0646", // abb june
                    "\u064a\u0648\u0644", // abb july
                    "\u0623\u063a\u0633", // abb august
                    "\u0633\u0628\u062a", // abb september
                    "\u0623\u0643\u062a", // abb october
                    "\u0646\u0648\u0641", // abb november
                    "\u062f\u064a\u0633", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "\u0627\u0644\u0623\u062d\u062f", // Sunday
                    "\u0627\u0644\u0627\u062b\u0646\u064a\u0646", // Monday
                    "\u0627\u0644\u062b\u0644\u0627\u062b\u0627\u0621", // Tuesday
                    "\u0627\u0644\u0623\u0631\u0628\u0639\u0627\u0621", // Wednesday
                    "\u0627\u0644\u062e\u0645\u064a\u0633", // Thursday
                    "\u0627\u0644\u062c\u0645\u0639\u0629", // Friday
                    "\u0627\u0644\u0633\u0628\u062a" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "\u062d", // abb Sunday
                    "\u0646", // abb Monday
                    "\u062b", // abb Tuesday
                    "\u0631", // abb Wednesday
                    "\u062e", // abb Thursday
                    "\u062c", // abb Friday
                    "\u0633" // abb Saturday
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "\u0635", // am marker
                    "\u0645" // pm marker
                }
            },
            { "Eras",
                new String[] { // era strings
                    "\u0642.\u0645",
                    "\u0645"
                }
            },
            { "NumberPatterns",
                new String[] {
                    "#,##0.###;#,##0.###-", // decimal pattern
                    "\u00A4 #,##0.###;\u00A4 #,##0.###-", // currency pattern
                    "#,##0%" // percent pattern
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "z hh:mm:ss a", // full time pattern
                    "z hh:mm:ss a", // long time pattern
                    "hh:mm:ss a", // medium time pattern
                    "hh:mm a", // short time pattern
                    "dd MMMM, yyyy", // full date pattern
                    "dd MMMM, yyyy", // long date pattern
                    "dd/MM/yyyy", // medium date pattern
                    "dd/MM/yy", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
        };
    }
}
