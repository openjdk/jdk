/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * COPYRIGHT AND PERMISSION NOTICE
 *
 * Copyright (C) 1991-2012 Unicode, Inc. All rights reserved. Distributed under
 * the Terms of Use in http://www.unicode.org/copyright.html.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of the Unicode data files and any associated documentation (the "Data
 * Files") or Unicode software and any associated documentation (the
 * "Software") to deal in the Data Files or Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, and/or sell copies of the Data Files or Software, and
 * to permit persons to whom the Data Files or Software are furnished to do so,
 * provided that (a) the above copyright notice(s) and this permission notice
 * appear with all copies of the Data Files or Software, (b) both the above
 * copyright notice(s) and this permission notice appear in associated
 * documentation, and (c) there is clear notice in each modified Data File or
 * in the Software as well as in the documentation associated with the Data
 * File(s) or Software that the data or software has been modified.
 *
 * THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
 * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF
 * THIRD PARTY RIGHTS. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS
 * INCLUDED IN THIS NOTICE BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR
 * CONSEQUENTIAL DAMAGES, OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
 * DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
 * OF THE DATA FILES OR SOFTWARE.
 *
 * Except as contained in this notice, the name of a copyright holder shall not
 * be used in advertising or otherwise to promote the sale, use or other
 * dealings in these Data Files or Software without prior written authorization
 * of the copyright holder.
 */

package sun.text.resources.ko;

import java.util.ListResourceBundle;

public class FormatData_ko extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    @Override
    protected final Object[][] getContents() {
        final String[] rocEras = {
            "\uc911\ud654\ubbfc\uad6d\uc804",
            "\uc911\ud654\ubbfc\uad6d",
        };
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
            { "DayNarrows",
                new String[] {
                    "\uc77c",
                    "\uc6d4",
                    "\ud654",
                    "\uc218",
                    "\ubaa9",
                    "\uae08",
                    "\ud1a0",
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "\uc624\uc804", // am marker
                    "\uc624\ud6c4" // pm marker
                }
            },
            { "TimePatterns",
                new String[] {
                    "a h'\uc2dc' mm'\ubd84' ss'\ucd08' z", // full time pattern
                    "a h'\uc2dc' mm'\ubd84' ss'\ucd08'", // long time pattern
                    "a h:mm:ss", // medium time pattern
                    "a h:mm", // short time pattern
                }
            },
            { "DatePatterns",
                new String[] {
                    "yyyy'\ub144' M'\uc6d4' d'\uc77c' EEEE", // full date pattern
                    "yyyy'\ub144' M'\uc6d4' d'\uc77c' '('EE')'", // long date pattern
                    "yyyy. M. d", // medium date pattern
                    "yy. M. d", // short date pattern
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GyMdkHmsSEDFwWahKzZ" },
            { "cldr.buddhist.DatePatterns",
                new String[] {
                    "G y\ub144 M\uc6d4 d\uc77c EEEE",
                    "G y\ub144 M\uc6d4 d\uc77c",
                    "G y. M. d",
                    "G y. M. d",
                }
            },
            { "cldr.japanese.DatePatterns",
                new String[] {
                    "G y\ub144 M\uc6d4 d\uc77c EEEE",
                    "G y\ub144 M\uc6d4 d\uc77c",
                    "G y. M. d",
                    "G y. M. d",
                }
            },
            { "roc.Eras", rocEras },
            { "roc.short.Eras", rocEras },
            { "cldr.roc.DatePatterns",
                new String[] {
                    "G y\ub144 M\uc6d4 d\uc77c EEEE",
                    "G y\ub144 M\uc6d4 d\uc77c",
                    "G y. M. d",
                    "G y. M. d",
                }
            },
            { "roc.DatePatterns",
                new String[] {
                    "GGGG y\ub144 M\uc6d4 d\uc77c EEEE",
                    "GGGG y\ub144 M\uc6d4 d\uc77c",
                    "GGGG y. M. d",
                    "GGGG y. M. d",
                }
            },
            { "calendarname.islamic-civil", "\uc774\uc2ac\ub78c \uc0c1\uc6a9\ub825" },
            { "calendarname.islamicc", "\uc774\uc2ac\ub78c \uc0c1\uc6a9\ub825" },
            { "calendarname.islamic", "\uc774\uc2ac\ub78c\ub825" },
            { "calendarname.japanese", "\uc77c\ubcf8\ub825" },
            { "calendarname.gregorian", "\ud0dc\uc591\ub825" },
            { "calendarname.gregory", "\ud0dc\uc591\ub825" },
            { "calendarname.roc", "\ub300\ub9cc\ub825" },
            { "calendarname.buddhist", "\ubd88\uad50\ub825" },
            { "field.era", "\uc5f0\ud638" },
            { "field.year", "\ub144" },
            { "field.month", "\uc6d4" },
            { "field.week", "\uc8fc" },
            { "field.weekday", "\uc694\uc77c" },
            { "field.dayperiod", "\uc624\uc804/\uc624\ud6c4" },
            { "field.hour", "\uc2dc" },
            { "field.minute", "\ubd84" },
            { "field.second", "\ucd08" },
            { "field.zone", "\uc2dc\uac04\ub300" },
        };
    }
}
