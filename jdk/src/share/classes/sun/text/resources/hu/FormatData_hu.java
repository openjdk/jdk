/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.text.resources.hu;

import java.util.ListResourceBundle;

public class FormatData_hu extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "janu\u00e1r", // january
                    "febru\u00e1r", // february
                    "m\u00e1rcius", // march
                    "\u00e1prilis", // april
                    "m\u00e1jus", // may
                    "j\u00fanius", // june
                    "j\u00falius", // july
                    "augusztus", // august
                    "szeptember", // september
                    "okt\u00f3ber", // october
                    "november", // november
                    "december", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "jan.", // abb january
                    "febr.", // abb february
                    "m\u00e1rc.", // abb march
                    "\u00e1pr.", // abb april
                    "m\u00e1j.", // abb may
                    "j\u00fan.", // abb june
                    "j\u00fal.", // abb july
                    "aug.", // abb august
                    "szept.", // abb september
                    "okt.", // abb october
                    "nov.", // abb november
                    "dec.", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "vas\u00e1rnap", // Sunday
                    "h\u00e9tf\u0151", // Monday
                    "kedd", // Tuesday
                    "szerda", // Wednesday
                    "cs\u00fct\u00f6rt\u00f6k", // Thursday
                    "p\u00e9ntek", // Friday
                    "szombat" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "V", // abb Sunday
                    "H", // abb Monday
                    "K", // abb Tuesday
                    "Sze", // abb Wednesday
                    "Cs", // abb Thursday
                    "P", // abb Friday
                    "Szo" // abb Saturday
                }
            },
            { "DayNarrows",
                new String[] {
                    "V",
                    "H",
                    "K",
                    "Sz",
                    "Cs",
                    "P",
                    "Sz",
                }
            },
            { "AmPmMarkers",
                new String[] {
                    "DE", // am marker
                    "DU" // pm marker
                }
            },
            { "Eras",
                new String[] { // era strings
                    "i.e.",
                    "i.u."
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
            { "TimePatterns",
                new String[] {
                    "H:mm:ss z", // full time pattern
                    "H:mm:ss z", // long time pattern
                    "H:mm:ss", // medium time pattern
                    "H:mm", // short time pattern
                }
            },
            { "DatePatterns",
                new String[] {
                    "yyyy. MMMM d.", // full date pattern
                    "yyyy. MMMM d.", // long date pattern
                    "yyyy.MM.dd.", // medium date pattern
                    "yyyy.MM.dd.", // short date pattern
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
            { "islamic.MonthNames",
                new String[] {
                    "Moharrem",
                    "Safar",
                    "R\u00e9bi el avvel",
                    "R\u00e9bi el accher",
                    "Dsem\u00e1di el avvel",
                    "Dsem\u00e1di el accher",
                    "Redseb",
                    "Sab\u00e1n",
                    "Ramad\u00e1n",
                    "Sevv\u00e1l",
                    "Ds\u00fcl kade",
                    "Ds\u00fcl hedse",
                    "",
                }
            },
            { "islamic.Eras",
                new String[] {
                    "",
                    "MF",
                }
            },
            { "calendarname.islamic-civil", "iszl\u00e1m civil napt\u00e1r" },
            { "calendarname.islamicc", "iszl\u00e1m civil napt\u00e1r" },
            { "calendarname.islamic", "iszl\u00e1m napt\u00e1r" },
            { "calendarname.japanese", "jap\u00e1n napt\u00e1r" },
            { "calendarname.gregorian", "Gergely-napt\u00e1r" },
            { "calendarname.gregory", "Gergely-napt\u00e1r" },
            { "calendarname.roc", "K\u00ednai k\u00f6zt\u00e1rsas\u00e1gi napt\u00e1r" },
            { "calendarname.buddhist", "buddhista napt\u00e1r" },
            { "field.era", "\u00e9ra" },
            { "field.year", "\u00e9v" },
            { "field.month", "h\u00f3nap" },
            { "field.week", "h\u00e9t" },
            { "field.weekday", "h\u00e9t napja" },
            { "field.dayperiod", "napszak" },
            { "field.hour", "\u00f3ra" },
            { "field.minute", "perc" },
            { "field.second", "m\u00e1sodperc" },
            { "field.zone", "z\u00f3na" },
        };
    }
}
