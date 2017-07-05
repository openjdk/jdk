/*
 * Portions Copyright 1998-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

public class FormatData_th extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        String[] dateTimePatterns = new String[] {
            "H' \u0e19\u0e32\u0e2c\u0e34\u0e01\u0e32 'm' \u0e19\u0e32\u0e17\u0e35 'ss' \u0e27\u0e34\u0e19\u0e32\u0e17\u0e35'", // full time pattern
            "H' \u0e19\u0e32\u0e2c\u0e34\u0e01\u0e32 'm' \u0e19\u0e32\u0e17\u0e35'", // long time pattern
            "H:mm:ss", // medium time pattern
            "H:mm' \u0e19.'",  // short time pattern (modified)  -- add ' \u0e19.'
                               // (it means something like "o'clock" in english)
            "EEEE'\u0e17\u0e35\u0e48 'd MMMM G yyyy", // full date pattern
            "d MMMM yyyy", // long date pattern
            "d MMM yyyy", // medium date pattern
            "d/M/yyyy", // short date pattern
            "{1}, {0}" // date-time pattern
        };

        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "\u0e21\u0e01\u0e23\u0e32\u0e04\u0e21", // january
                    "\u0e01\u0e38\u0e21\u0e20\u0e32\u0e1e\u0e31\u0e19\u0e18\u0e4c", // february
                    "\u0e21\u0e35\u0e19\u0e32\u0e04\u0e21", // march
                    "\u0e40\u0e21\u0e29\u0e32\u0e22\u0e19", // april
                    "\u0e1e\u0e24\u0e29\u0e20\u0e32\u0e04\u0e21", // may
                    "\u0e21\u0e34\u0e16\u0e38\u0e19\u0e32\u0e22\u0e19", // june
                    "\u0e01\u0e23\u0e01\u0e0e\u0e32\u0e04\u0e21", // july
                    "\u0e2a\u0e34\u0e07\u0e2b\u0e32\u0e04\u0e21", // august
                    "\u0e01\u0e31\u0e19\u0e22\u0e32\u0e22\u0e19", // september
                    "\u0e15\u0e38\u0e25\u0e32\u0e04\u0e21", // october
                    "\u0e1e\u0e24\u0e28\u0e08\u0e34\u0e01\u0e32\u0e22\u0e19", // november
                    "\u0e18\u0e31\u0e19\u0e27\u0e32\u0e04\u0e21", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "\u0e21.\u0e04.", // abb january
                    "\u0e01.\u0e1e.", // abb february
                    "\u0e21\u0e35.\u0e04.", // abb march
                    "\u0e40\u0e21.\u0e22.", // abb april
                    "\u0e1e.\u0e04.", // abb may
                    "\u0e21\u0e34.\u0e22.", // abb june
                    "\u0e01.\u0e04.", // abb july
                    "\u0e2a.\u0e04.", // abb august
                    "\u0e01.\u0e22.", // abb september
                    "\u0e15.\u0e04.", // abb october
                    "\u0e1e.\u0e22.", // abb november
                    "\u0e18.\u0e04.", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "\u0e27\u0e31\u0e19\u0e2d\u0e32\u0e17\u0e34\u0e15\u0e22\u0e4c", // Sunday
                    "\u0e27\u0e31\u0e19\u0e08\u0e31\u0e19\u0e17\u0e23\u0e4c", // Monday
                    "\u0e27\u0e31\u0e19\u0e2d\u0e31\u0e07\u0e04\u0e32\u0e23", // Tuesday
                    "\u0e27\u0e31\u0e19\u0e1e\u0e38\u0e18", // Wednesday
                    "\u0e27\u0e31\u0e19\u0e1e\u0e24\u0e2b\u0e31\u0e2a\u0e1a\u0e14\u0e35", // Thursday
                    "\u0e27\u0e31\u0e19\u0e28\u0e38\u0e01\u0e23\u0e4c", // Friday
                    "\u0e27\u0e31\u0e19\u0e40\u0e2a\u0e32\u0e23\u0e4c" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "\u0e2d\u0e32.", // abb Sunday
                    "\u0e08.", // abb Monday
                    "\u0e2d.", // abb Tuesday
                    "\u0e1e.", // abb Wednesday
                    "\u0e1e\u0e24.", // abb Thursday
                    "\u0e28.", // abb Friday
                    "\u0e2a." // abb Saturday
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "\u0e01\u0e48\u0e2d\u0e19\u0e40\u0e17\u0e35\u0e48\u0e22\u0e07", // am marker
                    "\u0e2b\u0e25\u0e31\u0e07\u0e40\u0e17\u0e35\u0e48\u0e22\u0e07" // pm marker
                }
            },
            { "sun.util.BuddhistCalendar.Eras",
                new String[] { // era strings
                    "\u0e1b\u0e35\u0e01\u0e48\u0e2d\u0e19\u0e04\u0e23\u0e34\u0e2a\u0e15\u0e4c\u0e01\u0e32\u0e25\u0e17\u0e35\u0e48",
                    "\u0E1E.\u0E28." // Thai calendar requires equivalent of B.E., Buddhist Era
                }
            },
            { "sun.util.BuddhistCalendar.short.Eras",
                new String[] { // era strings
                    "\u0e1b\u0e35\u0e01\u0e48\u0e2d\u0e19\u0e04\u0e23\u0e34\u0e2a\u0e15\u0e4c\u0e01\u0e32\u0e25\u0e17\u0e35\u0e48",
                    "\u0E1E.\u0E28." // Thai calendar requires equivalent of B.E., Buddhist Era
                }
            },
            { "Eras",
                new String[] { // era strings
                    "\u0e1b\u0e35\u0e01\u0e48\u0e2d\u0e19\u0e04\u0e23\u0e34\u0e2a\u0e15\u0e4c\u0e01\u0e32\u0e25\u0e17\u0e35\u0e48",
                    "\u0e04.\u0e28."
                }
            },
            { "sun.util.BuddhistCalendar.DateTimePatterns",
                dateTimePatterns
            },
            { "DateTimePatterns",
                dateTimePatterns
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
        };
    }
}
