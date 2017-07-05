/*
 * Portions Copyright 1996-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

public class FormatData_ko extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "1\uc6d4", // january
                    "2\uc6d4", // february
                    "3\uc6d4", // march
                    "4\uc6d4", // april
                    "5\uc6d4", // may
                    "6\uc6d4", // june
                    "7\uc6d4", // july
                    "8\uc6d4", // august
                    "9\uc6d4", // september
                    "10\uc6d4", // october
                    "11\uc6d4", // november
                    "12\uc6d4", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "1\uc6d4", // abb january
                    "2\uc6d4", // abb february
                    "3\uc6d4", // abb march
                    "4\uc6d4", // abb april
                    "5\uc6d4", // abb may
                    "6\uc6d4", // abb june
                    "7\uc6d4", // abb july
                    "8\uc6d4", // abb august
                    "9\uc6d4", // abb september
                    "10\uc6d4", // abb october
                    "11\uc6d4", // abb november
                    "12\uc6d4", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "\uc77c\uc694\uc77c", // Sunday
                    "\uc6d4\uc694\uc77c", // Monday
                    "\ud654\uc694\uc77c", // Tuesday
                    "\uc218\uc694\uc77c", // Wednesday
                    "\ubaa9\uc694\uc77c", // Thursday
                    "\uae08\uc694\uc77c", // Friday
                    "\ud1a0\uc694\uc77c" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "\uc77c", // abb Sunday
                    "\uc6d4", // abb Monday
                    "\ud654", // abb Tuesday
                    "\uc218", // abb Wednesday
                    "\ubaa9", // abb Thursday
                    "\uae08", // abb Friday
                    "\ud1a0" // abb Saturday
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "\uc624\uc804", // am marker
                    "\uc624\ud6c4" // pm marker
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "a h'\uc2dc' mm'\ubd84' ss'\ucd08' z", // full time pattern
                    "a h'\uc2dc' mm'\ubd84' ss'\ucd08'", // long time pattern
                    "a h:mm:ss", // medium time pattern
                    "a h:mm", // short time pattern
                    "yyyy'\ub144' M'\uc6d4' d'\uc77c' EEEE", // full date pattern
                    "yyyy'\ub144' M'\uc6d4' d'\uc77c' '('EE')'", // long date pattern
                    "yyyy. M. d", // medium date pattern
                    "yy. M. d", // short date pattern
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GyMdkHmsSEDFwWahKzZ" },
        };
    }
}
