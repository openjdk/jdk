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

public class FormatData_is extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "jan\u00faar", // january
                    "febr\u00faar", // february
                    "mars", // march
                    "apr\u00edl", // april
                    "ma\u00ed", // may
                    "j\u00fan\u00ed", // june
                    "j\u00fal\u00ed", // july
                    "\u00e1g\u00fast", // august
                    "september", // september
                    "okt\u00f3ber", // october
                    "n\u00f3vember", // november
                    "desember", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "jan.", // abb january
                    "feb.", // abb february
                    "mar.", // abb march
                    "apr.", // abb april
                    "ma\u00ed", // abb may
                    "j\u00fan.", // abb june
                    "j\u00fal.", // abb july
                    "\u00e1g\u00fa.", // abb august
                    "sep.", // abb september
                    "okt.", // abb october
                    "n\u00f3v.", // abb november
                    "des.", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "sunnudagur", // Sunday
                    "m\u00e1nudagur", // Monday
                    "\u00feri\u00f0judagur", // Tuesday
                    "mi\u00f0vikudagur", // Wednesday
                    "fimmtudagur", // Thursday
                    "f\u00f6studagur", // Friday
                    "laugardagur" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "sun.", // abb Sunday
                    "m\u00e1n.", // abb Monday
                    "\u00feri.", // abb Tuesday
                    "mi\u00f0.", // abb Wednesday
                    "fim.", // abb Thursday
                    "f\u00f6s.", // abb Friday
                    "lau." // abb Saturday
                }
            },
            { "NumberElements",
                new String[] {
                    ",", // decimal separator
                    ".", // group (thousands) separator
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
                    "HH:mm:ss z", // full time pattern
                    "HH:mm:ss z", // long time pattern
                    "HH:mm:ss", // medium time pattern
                    "HH:mm", // short time pattern
                    "d. MMMM yyyy", // full date pattern
                    "d. MMMM yyyy", // long date pattern
                    "d.M.yyyy", // medium date pattern
                    "d.M.yyyy", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GyMdkHmsSEDFwWahKzZ" },
        };
    }
}
