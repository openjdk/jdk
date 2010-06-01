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

public class FormatData_el extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "\u0399\u03b1\u03bd\u03bf\u03c5\u03ac\u03c1\u03b9\u03bf\u03c2", // january
                    "\u03a6\u03b5\u03b2\u03c1\u03bf\u03c5\u03ac\u03c1\u03b9\u03bf\u03c2", // february
                    "\u039c\u03ac\u03c1\u03c4\u03b9\u03bf\u03c2", // march
                    "\u0391\u03c0\u03c1\u03af\u03bb\u03b9\u03bf\u03c2", // april
                    "\u039c\u03ac\u03ca\u03bf\u03c2", // may
                    "\u0399\u03bf\u03cd\u03bd\u03b9\u03bf\u03c2", // june
                    "\u0399\u03bf\u03cd\u03bb\u03b9\u03bf\u03c2", // july
                    "\u0391\u03cd\u03b3\u03bf\u03c5\u03c3\u03c4\u03bf\u03c2", // august
                    "\u03a3\u03b5\u03c0\u03c4\u03ad\u03bc\u03b2\u03c1\u03b9\u03bf\u03c2", // september
                    "\u039f\u03ba\u03c4\u03ce\u03b2\u03c1\u03b9\u03bf\u03c2", // october
                    "\u039d\u03bf\u03ad\u03bc\u03b2\u03c1\u03b9\u03bf\u03c2", // november
                    "\u0394\u03b5\u03ba\u03ad\u03bc\u03b2\u03c1\u03b9\u03bf\u03c2", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "\u0399\u03b1\u03bd", // abb january
                    "\u03a6\u03b5\u03b2", // abb february
                    "\u039c\u03b1\u03c1", // abb march
                    "\u0391\u03c0\u03c1", // abb april
                    "\u039c\u03b1\u03ca", // abb may
                    "\u0399\u03bf\u03c5\u03bd", // abb june
                    "\u0399\u03bf\u03c5\u03bb", // abb july
                    "\u0391\u03c5\u03b3", // abb august
                    "\u03a3\u03b5\u03c0", // abb september
                    "\u039f\u03ba\u03c4", // abb october
                    "\u039d\u03bf\u03b5", // abb november
                    "\u0394\u03b5\u03ba", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "\u039a\u03c5\u03c1\u03b9\u03b1\u03ba\u03ae", // Sunday
                    "\u0394\u03b5\u03c5\u03c4\u03ad\u03c1\u03b1", // Monday
                    "\u03a4\u03c1\u03af\u03c4\u03b7", // Tuesday
                    "\u03a4\u03b5\u03c4\u03ac\u03c1\u03c4\u03b7", // Wednesday
                    "\u03a0\u03ad\u03bc\u03c0\u03c4\u03b7", // Thursday
                    "\u03a0\u03b1\u03c1\u03b1\u03c3\u03ba\u03b5\u03c5\u03ae", // Friday
                    "\u03a3\u03ac\u03b2\u03b2\u03b1\u03c4\u03bf" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "\u039a\u03c5\u03c1", // abb Sunday
                    "\u0394\u03b5\u03c5", // abb Monday
                    "\u03a4\u03c1\u03b9", // abb Tuesday
                    "\u03a4\u03b5\u03c4", // abb Wednesday
                    "\u03a0\u03b5\u03bc", // abb Thursday
                    "\u03a0\u03b1\u03c1", // abb Friday
                    "\u03a3\u03b1\u03b2" // abb Saturday
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "\u03c0\u03bc", // am marker
                    "\u03bc\u03bc" // pm marker
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
                    "h:mm:ss a z", // full time pattern
                    "h:mm:ss a z", // long time pattern
                    "h:mm:ss a", // medium time pattern
                    "h:mm a", // short time pattern
                    "EEEE, d MMMM yyyy", // full date pattern
                    "d MMMM yyyy", // long date pattern
                    "d MMM yyyy", // medium date pattern
                    "d/M/yyyy", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
        };
    }
}
