/*
 * Copyright (c) 1996, 2005, Oracle and/or its affiliates. All rights reserved.
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

public class FormatData extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "January", // january
                    "February", // february
                    "March", // march
                    "April", // april
                    "May", // may
                    "June", // june
                    "July", // july
                    "August", // august
                    "September", // september
                    "October", // october
                    "November", // november
                    "December", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "Jan", // abb january
                    "Feb", // abb february
                    "Mar", // abb march
                    "Apr", // abb april
                    "May", // abb may
                    "Jun", // abb june
                    "Jul", // abb july
                    "Aug", // abb august
                    "Sep", // abb september
                    "Oct", // abb october
                    "Nov", // abb november
                    "Dec", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "Sunday", // Sunday
                    "Monday", // Monday
                    "Tuesday", // Tuesday
                    "Wednesday", // Wednesday
                    "Thursday", // Thursday
                    "Friday", // Friday
                    "Saturday" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "Sun", // abb Sunday
                    "Mon", // abb Monday
                    "Tue", // abb Tuesday
                    "Wed", // abb Wednesday
                    "Thu", // abb Thursday
                    "Fri", // abb Friday
                    "Sat" // abb Saturday
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "AM", // am marker
                    "PM" // pm marker
                }
            },
            { "Eras",
                new String[] { // era strings for GregorianCalendar
                    "BC",
                    "AD"
                }
            },
            { "sun.util.BuddhistCalendar.Eras",
                new String[] { // Thai Buddhist calendar era strings
                    "BC",     // BC
                    "B.E."    // Buddhist Era
                }
            },
            { "sun.util.BuddhistCalendar.short.Eras",
                new String[] { // Thai Buddhist calendar era strings
                    "BC",     // BC
                    "B.E."    // Buddhist Era
                }
            },
            { "java.util.JapaneseImperialCalendar.Eras",
                new String[] { // Japanese imperial calendar era strings
                    "",
                    "Meiji",
                    "Taisho",
                    "Showa",
                    "Heisei",
                }
            },
            { "java.util.JapaneseImperialCalendar.short.Eras",
                new String[] { // Japanese imperial calendar era abbreviations
                    "",
                    "M",
                    "T",
                    "S",
                    "H",
                }
            },
            { "java.util.JapaneseImperialCalendar.FirstYear",
                new String[] { // Japanese imperial calendar year name
                    // empty in English
                }
            },
            { "NumberPatterns",
                new String[] {
                    "#,##0.###;-#,##0.###", // decimal pattern
                    "\u00a4 #,##0.00;-\u00a4 #,##0.00", // currency pattern
                    "#,##0%" // percent pattern
                }
            },
            { "NumberElements",
                new String[] {
                    ".", // decimal separator
                    ",", // group (thousands) separator
                    ";", // list separator
                    "%", // percent sign
                    "0", // native 0 digit
                    "#", // pattern digit
                    "-", // minus sign
                    "E", // exponential
                    "\u2030", // per mille
                    "\u221e", // infinity
                    "\ufffd" // NaN
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "h:mm:ss a z",        // full time pattern
                    "h:mm:ss a z",        // long time pattern
                    "h:mm:ss a",          // medium time pattern
                    "h:mm a",             // short time pattern
                    "EEEE, MMMM d, yyyy", // full date pattern
                    "MMMM d, yyyy",       // long date pattern
                    "MMM d, yyyy",        // medium date pattern
                    "M/d/yy",             // short date pattern
                    "{1} {0}"             // date-time pattern
                }
            },
            { "sun.util.BuddhistCalendar.DateTimePatterns",
                new String[] {
                    "H:mm:ss z",          // full time pattern
                    "H:mm:ss z",          // long time pattern
                    "H:mm:ss",            // medium time pattern
                    "H:mm",               // short time pattern
                    "EEEE d MMMM G yyyy", // full date pattern
                    "d MMMM yyyy",        // long date pattern
                    "d MMM yyyy",         // medium date pattern
                    "d/M/yyyy",           // short date pattern
                    "{1}, {0}"            // date-time pattern
                }
            },
            { "java.util.JapaneseImperialCalendar.DateTimePatterns",
                new String[] {
                    "h:mm:ss a z",             // full time pattern
                    "h:mm:ss a z",             // long time pattern
                    "h:mm:ss a",               // medium time pattern
                    "h:mm a",                  // short time pattern
                    "GGGG yyyy MMMM d (EEEE)", // full date pattern
                    "GGGG yyyy MMMM d",        // long date pattern
                    "GGGG yyyy MMM d",         // medium date pattern
                    "Gy.MM.dd",                // short date pattern
                    "{1} {0}"                  // date-time pattern
                }
            },
            { "DateTimePatternChars", "GyMdkHmsSEDFwWahKzZ" },
        };
    }
}
