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

public class FormatData_ru extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "\u042f\u043d\u0432\u0430\u0440\u044c", // january
                    "\u0424\u0435\u0432\u0440\u0430\u043b\u044c", // february
                    "\u041c\u0430\u0440\u0442", // march
                    "\u0410\u043f\u0440\u0435\u043b\u044c", // april
                    "\u041c\u0430\u0439", // may
                    "\u0418\u044e\u043d\u044c", // june
                    "\u0418\u044e\u043b\u044c", // july
                    "\u0410\u0432\u0433\u0443\u0441\u0442", // august
                    "\u0421\u0435\u043d\u0442\u044f\u0431\u0440\u044c", // september
                    "\u041e\u043a\u0442\u044f\u0431\u0440\u044c", // october
                    "\u041d\u043e\u044f\u0431\u0440\u044c", // november
                    "\u0414\u0435\u043a\u0430\u0431\u0440\u044c", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "\u044f\u043d\u0432", // abb january
                    "\u0444\u0435\u0432", // abb february
                    "\u043c\u0430\u0440", // abb march
                    "\u0430\u043f\u0440", // abb april
                    "\u043c\u0430\u0439", // abb may
                    "\u0438\u044e\u043d", // abb june
                    "\u0438\u044e\u043b", // abb july
                    "\u0430\u0432\u0433", // abb august
                    "\u0441\u0435\u043d", // abb september
                    "\u043e\u043a\u0442", // abb october
                    "\u043d\u043e\u044f", // abb november
                    "\u0434\u0435\u043a", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "\u0432\u043e\u0441\u043a\u0440\u0435\u0441\u0435\u043d\u044c\u0435", // Sunday
                    "\u043f\u043e\u043d\u0435\u0434\u0435\u043b\u044c\u043d\u0438\u043a", // Monday
                    "\u0432\u0442\u043e\u0440\u043d\u0438\u043a", // Tuesday
                    "\u0441\u0440\u0435\u0434\u0430", // Wednesday
                    "\u0447\u0435\u0442\u0432\u0435\u0440\u0433", // Thursday
                    "\u043f\u044f\u0442\u043d\u0438\u0446\u0430", // Friday
                    "\u0441\u0443\u0431\u0431\u043e\u0442\u0430" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "\u0412\u0441", // abb Sunday
                    "\u041f\u043d", // abb Monday
                    "\u0412\u0442", // abb Tuesday
                    "\u0421\u0440", // abb Wednesday
                    "\u0427\u0442", // abb Thursday
                    "\u041f\u0442", // abb Friday
                    "\u0421\u0431" // abb Saturday
                }
            },
            { "Eras",
                new String[] { // era strings
                    "\u0434\u043e \u043d.\u044d.",
                    "\u043d.\u044d."
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
                    "d MMMM yyyy '\u0433.'", // full date pattern
                    "d MMMM yyyy '\u0433.'", // long date pattern
                    "dd.MM.yyyy", // medium date pattern
                    "dd.MM.yy", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
        };
    }
}
