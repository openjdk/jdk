/*
 * Portions Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

public class FormatData_et extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "jaanuar", // january
                    "veebruar", // february
                    "m\u00e4rts", // march
                    "aprill", // april
                    "mai", // may
                    "juuni", // june
                    "juuli", // july
                    "august", // august
                    "september", // september
                    "oktoober", // october
                    "november", // november
                    "detsember", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "jaan", // abb january
                    "veebr", // abb february
                    "m\u00e4rts", // abb march
                    "apr", // abb april
                    "mai", // abb may
                    "juuni", // abb june
                    "juuli", // abb july
                    "aug", // abb august
                    "sept", // abb september
                    "okt", // abb october
                    "nov", // abb november
                    "dets", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "p\u00fchap\u00e4ev", // Sunday
                    "esmasp\u00e4ev", // Monday
                    "teisip\u00e4ev", // Tuesday
                    "kolmap\u00e4ev", // Wednesday
                    "neljap\u00e4ev", // Thursday
                    "reede", // Friday
                    "laup\u00e4ev" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "P", // abb Sunday
                    "E", // abb Monday
                    "T", // abb Tuesday
                    "K", // abb Wednesday
                    "N", // abb Thursday
                    "R", // abb Friday
                    "L" // abb Saturday
                }
            },
            { "Eras",
                new String[] { // era strings
                    "e.m.a.",
                    "m.a.j."
                }
            },
            { "NumberElements",
                new String[] {
                    ",", // decimal separator
                    "\u00a0", // group (thousands) separator
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
                    "H:mm:ss z", // full time pattern
                    "H:mm:ss z", // long time pattern
                    "H:mm:ss", // medium time pattern
                    "H:mm", // short time pattern
                    "EEEE, d. MMMM yyyy", // full date pattern
                    "EEEE, d. MMMM yyyy. 'a'", // long date pattern
                    "d.MM.yyyy", // medium date pattern
                    "d.MM.yy", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
        };
    }
}
