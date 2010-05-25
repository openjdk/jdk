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
 * (C) Copyright IBM Corp. 1996 - 1999 - All Rights Reserved
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

public class FormatData_zh extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "\u4e00\u6708", // january
                    "\u4e8c\u6708", // february
                    "\u4e09\u6708", // march
                    "\u56db\u6708", // april
                    "\u4e94\u6708", // may
                    "\u516d\u6708", // june
                    "\u4e03\u6708", // july
                    "\u516b\u6708", // august
                    "\u4e5d\u6708", // september
                    "\u5341\u6708", // october
                    "\u5341\u4e00\u6708", // november
                    "\u5341\u4e8c\u6708", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "\u4e00\u6708", // abb january
                    "\u4e8c\u6708", // abb february
                    "\u4e09\u6708", // abb march
                    "\u56db\u6708", // abb april
                    "\u4e94\u6708", // abb may
                    "\u516d\u6708", // abb june
                    "\u4e03\u6708", // abb july
                    "\u516b\u6708", // abb august
                    "\u4e5d\u6708", // abb september
                    "\u5341\u6708", // abb october
                    "\u5341\u4e00\u6708", // abb november
                    "\u5341\u4e8c\u6708", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "\u661f\u671f\u65e5", // Sunday
                    "\u661f\u671f\u4e00", // Monday
                    "\u661f\u671f\u4e8c", // Tuesday
                    "\u661f\u671f\u4e09", // Wednesday
                    "\u661f\u671f\u56db", // Thursday
                    "\u661f\u671f\u4e94", // Friday
                    "\u661f\u671f\u516d" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "\u661f\u671f\u65e5", // abb Sunday
                    "\u661f\u671f\u4e00", // abb Monday
                    "\u661f\u671f\u4e8c", // abb Tuesday
                    "\u661f\u671f\u4e09", // abb Wednesday
                    "\u661f\u671f\u56db", // abb Thursday
                    "\u661f\u671f\u4e94", // abb Friday
                    "\u661f\u671f\u516d" // abb Saturday
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "\u4e0a\u5348", // am marker
                    "\u4e0b\u5348" // pm marker
                }
            },
            { "Eras",
                new String[] { // era strings
                    "\u516c\u5143\u524d",
                    "\u516c\u5143"
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "ahh'\u65f6'mm'\u5206'ss'\u79d2' z", // full time pattern
                    "ahh'\u65f6'mm'\u5206'ss'\u79d2'", // long time pattern
                    "H:mm:ss", // medium time pattern
                    "ah:mm", // short time pattern
                    "yyyy'\u5e74'M'\u6708'd'\u65e5' EEEE", // full date pattern
                    "yyyy'\u5e74'M'\u6708'd'\u65e5'", // long date pattern
                    "yyyy-M-d", // medium date pattern
                    "yy-M-d", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
        };
    }
}
