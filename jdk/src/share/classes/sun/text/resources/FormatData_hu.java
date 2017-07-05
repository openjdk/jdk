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

public class FormatData_hu extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "janu\u00e1r", // january
                    "febru\u00e1r", // february
                    "m\u00e1rcius", // march
                    "\u00e1prilis", // april
                    "m\u00e1jus", // may
                    "j\u00fanius", // june
                    "j\u00falius", // july
                    "augusztus", // august
                    "szeptember", // september
                    "okt\u00f3ber", // october
                    "november", // november
                    "december", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "jan.", // abb january
                    "febr.", // abb february
                    "m\u00e1rc.", // abb march
                    "\u00e1pr.", // abb april
                    "m\u00e1j.", // abb may
                    "j\u00fan.", // abb june
                    "j\u00fal.", // abb july
                    "aug.", // abb august
                    "szept.", // abb september
                    "okt.", // abb october
                    "nov.", // abb november
                    "dec.", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "vas\u00e1rnap", // Sunday
                    "h\u00e9tf\u0151", // Monday
                    "kedd", // Tuesday
                    "szerda", // Wednesday
                    "cs\u00fct\u00f6rt\u00f6k", // Thursday
                    "p\u00e9ntek", // Friday
                    "szombat" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "V", // abb Sunday
                    "H", // abb Monday
                    "K", // abb Tuesday
                    "Sze", // abb Wednesday
                    "Cs", // abb Thursday
                    "P", // abb Friday
                    "Szo" // abb Saturday
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "DE", // am marker
                    "DU" // pm marker
                }
            },
            { "Eras",
                new String[] { // era strings
                    "i.e.",
                    "i.u."
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
                    "yyyy. MMMM d.", // full date pattern
                    "yyyy. MMMM d.", // long date pattern
                    "yyyy.MM.dd.", // medium date pattern
                    "yyyy.MM.dd.", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
        };
    }
}
