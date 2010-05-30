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

public class FormatData_fr extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "janvier", // january
                    "f\u00e9vrier", // february
                    "mars", // march
                    "avril", // april
                    "mai", // may
                    "juin", // june
                    "juillet", // july
                    "ao\u00fbt", // august
                    "septembre", // september
                    "octobre", // october
                    "novembre", // november
                    "d\u00e9cembre", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "janv.", // abb january
                    "f\u00e9vr.", // abb february
                    "mars", // abb march
                    "avr.", // abb april
                    "mai", // abb may
                    "juin", // abb june
                    "juil.", // abb july
                    "ao\u00fbt", // abb august
                    "sept.", // abb september
                    "oct.", // abb october
                    "nov.", // abb november
                    "d\u00e9c.", // abb december
                    "" // abb mo month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "dimanche", // Sunday
                    "lundi", // Monday
                    "mardi", // Tuesday
                    "mercredi", // Wednesday
                    "jeudi", // Thursday
                    "vendredi", // Friday
                    "samedi" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "dim.", // abb Sunday
                    "lun.", // abb Monday
                    "mar.", // abb Tuesday
                    "mer.", // abb Wednesday
                    "jeu.", // abb Thursday
                    "ven.", // abb Friday
                    "sam." // abb Saturday
                }
            },
            { "Eras",
                new String[] { // era strings
                    "BC",
                    "ap. J.-C."
                }
            },
            { "NumberPatterns",
                new String[] {
                    "#,##0.###;-#,##0.###", // decimal pattern
                    "#,##0.00 \u00A4;-#,##0.00 \u00A4", // currency pattern
                    "#,##0 %" // percent pattern
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
                    "HH' h 'mm z", // full time pattern
                    "HH:mm:ss z", // long time pattern
                    "HH:mm:ss", // medium time pattern
                    "HH:mm", // short time pattern
                    "EEEE d MMMM yyyy", // full date pattern
                    "d MMMM yyyy", // long date pattern
                    "d MMM yyyy", // medium date pattern
                    "dd/MM/yy", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GaMjkHmsSEDFwWahKzZ" },
        };
    }
}
