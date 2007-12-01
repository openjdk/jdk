/*
 * Portions Copyright 1996-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1999 - All Rights Reserved
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

public class FormatData_ja extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "1\u6708", // january
                    "2\u6708", // february
                    "3\u6708", // march
                    "4\u6708", // april
                    "5\u6708", // may
                    "6\u6708", // june
                    "7\u6708", // july
                    "8\u6708", // august
                    "9\u6708", // september
                    "10\u6708", // october
                    "11\u6708", // november
                    "12\u6708", // december
                    ""          // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "1", // abb january
                    "2", // abb february
                    "3", // abb march
                    "4", // abb april
                    "5", // abb may
                    "6", // abb june
                    "7", // abb july
                    "8", // abb august
                    "9", // abb september
                    "10", // abb october
                    "11", // abb november
                    "12", // abb december
                    ""    // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "\u65e5\u66dc\u65e5", // Sunday
                    "\u6708\u66dc\u65e5", // Monday
                    "\u706b\u66dc\u65e5", // Tuesday
                    "\u6c34\u66dc\u65e5", // Wednesday
                    "\u6728\u66dc\u65e5", // Thursday
                    "\u91d1\u66dc\u65e5", // Friday
                    "\u571f\u66dc\u65e5"  // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "\u65e5", // abb Sunday
                    "\u6708", // abb Monday
                    "\u706b", // abb Tuesday
                    "\u6c34", // abb Wednesday
                    "\u6728", // abb Thursday
                    "\u91d1", // abb Friday
                    "\u571f"  // abb Saturday
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "\u5348\u524d", // am marker
                    "\u5348\u5f8c" // pm marker
                }
            },
            { "Eras",
                new String[] { // era strings for GregorianCalendar
                    "\u7d00\u5143\u524d",
                    "\u897f\u66a6"
                }
            },
            { "sun.util.BuddhistCalendar.Eras",
                new String[] { // era strings for Thai Buddhist calendar
                    "\u7d00\u5143\u524d", // Kigenzen
                    "\u4ecf\u66a6",       // Butsureki
                }
            },
            { "java.util.JapaneseImperialCalendar.Eras",
                new String[] { // era strings for Japanese imperial calendar
                    "\u897f\u66a6",     // Seireki (Gregorian)
                    "\u660e\u6cbb",     // Meiji
                    "\u5927\u6b63",     // Taisho
                    "\u662d\u548c",     // Showa
                    "\u5e73\u6210",     // Heisei
                }
            },
            { "java.util.JapaneseImperialCalendar.FirstYear",
                new String[] {  // first year name
                    "\u5143",   // "Gan"-nen
                }
            },
            { "NumberElements",
                new String[] {
                    ".",        // decimal separator
                    ",",        // group (thousands) separator
                    ";",        // list separator
                    "%",        // percent sign
                    "0",        // native 0 digit
                    "#",        // pattern digit
                    "-",        // minus sign
                    "E",        // exponential
                    "\u2030",   // per mille
                    "\u221e",   // infinity
                    "\ufffd"    // NaN
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "H'\u6642'mm'\u5206'ss'\u79d2' z", // full time pattern
                    "H:mm:ss z",                       // long time pattern
                    "H:mm:ss",                         // medium time pattern
                    "H:mm",                            // short time pattern
                    "yyyy'\u5e74'M'\u6708'd'\u65e5'",  // full date pattern
                    "yyyy/MM/dd",                      // long date pattern
                    "yyyy/MM/dd",                      // medium date pattern
                    "yy/MM/dd",                        // short date pattern
                    "{1} {0}"                          // date-time pattern
                }
            },
            { "java.util.JapaneseImperialCalendar.DateTimePatterns",
                new String[] {
                    "H'\u6642'mm'\u5206'ss'\u79d2' z", // full time pattern
                    "H:mm:ss z", // long time pattern
                    "H:mm:ss",   // medium time pattern
                    "H:mm",      // short time pattern
                    "GGGGyyyy'\u5e74'M'\u6708'd'\u65e5'", // full date pattern
                    "Gy.MM.dd",  // long date pattern
                    "Gy.MM.dd",  // medium date pattern
                    "Gy.MM.dd",  // short date pattern
                    "{1} {0}"    // date-time pattern
                }
            },
            { "DateTimePatternChars", "GyMdkHmsSEDFwWahKzZ" },
        };
    }
}
