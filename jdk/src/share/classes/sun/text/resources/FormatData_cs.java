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

public class FormatData_cs extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "leden", // january
                    "\u00fanor", // february
                    "b\u0159ezen", // march
                    "duben", // april
                    "kv\u011bten", // may
                    "\u010derven", // june
                    "\u010dervenec", // july
                    "srpen", // august
                    "z\u00e1\u0159\u00ed", // september
                    "\u0159\u00edjen", // october
                    "listopad", // november
                    "prosinec", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "I", // abb january
                    "II", // abb february
                    "III", // abb march
                    "IV", // abb april
                    "V", // abb may
                    "VI", // abb june
                    "VII", // abb july
                    "VIII", // abb august
                    "IX", // abb september
                    "X", // abb october
                    "XI", // abb november
                    "XII", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "Ned\u011ble", // Sunday
                    "Pond\u011bl\u00ed", // Monday
                    "\u00dater\u00fd", // Tuesday
                    "St\u0159eda", // Wednesday
                    "\u010ctvrtek", // Thursday
                    "P\u00e1tek", // Friday
                    "Sobota" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "Ne", // abb Sunday
                    "Po", // abb Monday
                    "\u00dat", // abb Tuesday
                    "St", // abb Wednesday
                    "\u010ct", // abb Thursday
                    "P\u00e1", // abb Friday
                    "So" // abb Saturday
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "dop.", // am marker
                    "odp." // pm marker
                }
            },
            { "Eras",
                new String[] { // era strings
                    "p\u0159.Kr.",
                    "po Kr."
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
                    "d. MMMM yyyy", // long date pattern
                    "d.M.yyyy", // medium date pattern
                    "d.M.yy", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GuMtkHmsSEDFwWahKzZ" },
        };
    }
}
