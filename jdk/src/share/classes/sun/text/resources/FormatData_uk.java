/*
 * Portions Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

public class FormatData_uk extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "\u0441\u0456\u0447\u043d\u044f", // january
                    "\u043b\u044e\u0442\u043e\u0433\u043e", // february
                    "\u0431\u0435\u0440\u0435\u0437\u043d\u044f", // march
                    "\u043a\u0432\u0456\u0442\u043d\u044f", // april
                    "\u0442\u0440\u0430\u0432\u043d\u044f", // may
                    "\u0447\u0435\u0440\u0432\u043d\u044f", // june
                    "\u043b\u0438\u043f\u043d\u044f", // july
                    "\u0441\u0435\u0440\u043f\u043d\u044f", // august
                    "\u0432\u0435\u0440\u0435\u0441\u043d\u044f", // september
                    "\u0436\u043e\u0432\u0442\u043d\u044f", // october
                    "\u043b\u0438\u0441\u0442\u043e\u043f\u0430\u0434\u0430", // november
                    "\u0433\u0440\u0443\u0434\u043d\u044f", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "\u0441\u0456\u0447", // abb january
                    "\u043b\u044e\u0442", // abb february
                    "\u0431\u0435\u0440", // abb march
                    "\u043a\u0432\u0456\u0442", // abb april
                    "\u0442\u0440\u0430\u0432", // abb may
                    "\u0447\u0435\u0440\u0432", // abb june
                    "\u043b\u0438\u043f", // abb july
                    "\u0441\u0435\u0440\u043f", // abb august
                    "\u0432\u0435\u0440", // abb september
                    "\u0436\u043e\u0432\u0442", // abb october
                    "\u043b\u0438\u0441\u0442", // abb november
                    "\u0433\u0440\u0443\u0434", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "\u043d\u0435\u0434\u0456\u043b\u044f", // Sunday
                    "\u043f\u043e\u043d\u0435\u0434\u0456\u043b\u043e\u043a", // Monday
                    "\u0432\u0456\u0432\u0442\u043e\u0440\u043e\u043a", // Tuesday
                    "\u0441\u0435\u0440\u0435\u0434\u0430", // Wednesday
                    "\u0447\u0435\u0442\u0432\u0435\u0440", // Thursday
                    "\u043f'\u044f\u0442\u043d\u0438\u0446\u044f", // Friday
                    "\u0441\u0443\u0431\u043e\u0442\u0430" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "\u043d\u0434", // abb Sunday
                    "\u043f\u043d", // abb Monday
                    "\u0432\u0442", // abb Tuesday
                    "\u0441\u0440", // abb Wednesday
                    "\u0447\u0442", // abb Thursday
                    "\u043f\u0442", // abb Friday
                    "\u0441\u0431" // abb Saturday
                }
            },
            { "Eras",
                new String[] { // era strings
                    "\u0434\u043e \u043d.\u0435.",
                    "\u043f\u0456\u0441\u043b\u044f \u043d.\u0435."
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
                    "EEEE, d MMMM yyyy \u0440.", // full date pattern
                    "d MMMM yyyy", // long date pattern
                    "d MMM yyyy", // medium date pattern
                    "dd.MM.yy", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
        };
    }
}
