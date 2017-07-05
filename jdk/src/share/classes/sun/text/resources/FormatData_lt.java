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

public class FormatData_lt extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "Sausio", // january
                    "Vasario", // february
                    "Kovo", // march
                    "Baland\u017eio", // april
                    "Gegu\u017e\u0117s", // may
                    "Bir\u017eelio", // june
                    "Liepos", // july
                    "Rugpj\u016b\u010dio", // august
                    "Rugs\u0117jo", // september
                    "Spalio", // october
                    "Lapkri\u010dio", // november
                    "Gruod\u017eio", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "Sau", // abb january
                    "Vas", // abb february
                    "Kov", // abb march
                    "Bal", // abb april
                    "Geg", // abb may
                    "Bir", // abb june
                    "Lie", // abb july
                    "Rgp", // abb august
                    "Rgs", // abb september
                    "Spa", // abb october
                    "Lap", // abb november
                    "Grd", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "Sekmadienis", // Sunday
                    "Pirmadienis", // Monday
                    "Antradienis", // Tuesday
                    "Tre\u010diadienis", // Wednesday
                    "Ketvirtadienis", // Thursday
                    "Penktadienis", // Friday
                    "\u0160e\u0161tadienis" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "Sk", // abb Sunday
                    "Pr", // abb Monday
                    "An", // abb Tuesday
                    "Tr", // abb Wednesday
                    "Kt", // abb Thursday
                    "Pn", // abb Friday
                    "\u0160t" // abb Saturday
                }
            },
            { "Eras",
                new String[] { // era strings
                    "pr.Kr.",
                    "po.Kr."
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
                    "HH.mm.ss z", // full time pattern
                    "HH.mm.ss z", // long time pattern
                    "HH.mm.ss", // medium time pattern
                    "HH.mm", // short time pattern
                    "EEEE, yyyy, MMMM d", // full date pattern
                    "EEEE, yyyy, MMMM d", // long date pattern
                    "yyyy-MM-dd", // medium date pattern
                    "yy.M.d", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
        };
    }
}
