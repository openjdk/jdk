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

package sun.text.resources.tr;

import java.util.ListResourceBundle;

public class FormatData_tr extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "Ocak", // january
                    "\u015eubat", // february
                    "Mart", // march
                    "Nisan", // april
                    "May\u0131s", // may
                    "Haziran", // june
                    "Temmuz", // july
                    "A\u011fustos", // august
                    "Eyl\u00fcl", // september
                    "Ekim", // october
                    "Kas\u0131m", // november
                    "Aral\u0131k", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "Oca", // abb january
                    "\u015eub", // abb february
                    "Mar", // abb march
                    "Nis", // abb april
                    "May", // abb may
                    "Haz", // abb june
                    "Tem", // abb july
                    "A\u011fu", // abb august
                    "Eyl", // abb september
                    "Eki", // abb october
                    "Kas", // abb november
                    "Ara", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "standalone.MonthNarrows",
                new String[] {
                    "O",
                    "\u015e",
                    "M",
                    "N",
                    "M",
                    "H",
                    "T",
                    "A",
                    "E",
                    "E",
                    "K",
                    "A",
                    "",
                }
            },
            { "DayNames",
                new String[] {
                    "Pazar", // Sunday
                    "Pazartesi", // Monday
                    "Sal\u0131", // Tuesday
                    "\u00c7ar\u015famba", // Wednesday
                    "Per\u015fembe", // Thursday
                    "Cuma", // Friday
                    "Cumartesi" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "Paz", // abb Sunday
                    "Pzt", // abb Monday
                    "Sal", // abb Tuesday
                    "\u00c7ar", // abb Wednesday
                    "Per", // abb Thursday
                    "Cum", // abb Friday
                    "Cmt" // abb Saturday
                }
            },
            { "DayNarrows",
                new String[] {
                    "P",
                    "P",
                    "S",
                    "\u00c7",
                    "P",
                    "C",
                    "C",
                }
            },
            { "NumberPatterns",
                new String[] {
                    "#,##0.###;-#,##0.###", // decimal pattern
                    "#,##0.00 \u00a4;-#,##0.00 \u00a4", // currency pattern
                    "% #,##0" // percent pattern
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
            { "TimePatterns",
                new String[] {
                    "HH:mm:ss z", // full time pattern
                    "HH:mm:ss z", // long time pattern
                    "HH:mm:ss", // medium time pattern
                    "HH:mm", // short time pattern
                }
            },
            { "DatePatterns",
                new String[] {
                    "dd MMMM yyyy EEEE", // full date pattern
                    "dd MMMM yyyy EEEE", // long date pattern
                    "dd.MMM.yyyy", // medium date pattern
                    "dd.MM.yyyy", // short date pattern
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
            { "cldr.buddhist.DatePatterns",
                new String[] {
                    "dd MMMM y G EEEE",
                    "dd MMMM y G",
                    "dd MMM y G",
                    "dd.MM.yyyy G",
                }
            },
            { "cldr.japanese.DatePatterns",
                new String[] {
                    "dd MMMM y G EEEE",
                    "dd MMMM y G",
                    "dd MMM y G",
                    "dd.MM.yyyy G",
                }
            },
            { "cldr.roc.DatePatterns",
                new String[] {
                    "dd MMMM y G EEEE",
                    "dd MMMM y G",
                    "dd MMM y G",
                    "dd.MM.yyyy G",
                }
            },
            { "roc.DatePatterns",
                new String[] {
                    "dd MMMM y GGGG EEEE",
                    "dd MMMM y GGGG",
                    "dd MMM y GGGG",
                    "dd.MM.yyyy GGGG",
                }
            },
            { "islamic.MonthNames",
                new String[] {
                    "Muharrem",
                    "Safer",
                    "Rebi\u00fclevvel",
                    "Rebi\u00fclahir",
                    "Cemaziyelevvel",
                    "Cemaziyelahir",
                    "Recep",
                    "\u015eaban",
                    "Ramazan",
                    "\u015eevval",
                    "Zilkade",
                    "Zilhicce",
                    "",
                }
            },
            { "cldr.islamic.DatePatterns",
                new String[] {
                    "dd MMMM y G EEEE",
                    "dd MMMM y G",
                    "dd MMM y G",
                    "dd.MM.yyyy G",
                }
            },
            { "islamic.DatePatterns",
                new String[] {
                    "dd MMMM y GGGG EEEE",
                    "dd MMMM y GGGG",
                    "dd MMM y GGGG",
                    "dd.MM.yyyy GGGG",
                }
            },
            { "calendarname.islamic-civil", "Arap Takvimi" },
            { "calendarname.islamicc", "Arap Takvimi" },
            { "calendarname.islamic", "Hicri Takvim" },
            { "calendarname.japanese", "Japon Takvimi" },
            { "calendarname.gregorian", "Miladi Takvim" },
            { "calendarname.gregory", "Miladi Takvim" },
            { "calendarname.roc", "\u00c7in Cumhuriyeti Takvimi" },
            { "calendarname.buddhist", "Budist Takvimi" },
            { "field.era", "Miladi D\u00f6nem" },
            { "field.year", "Y\u0131l" },
            { "field.month", "Ay" },
            { "field.week", "Hafta" },
            { "field.weekday", "Haftan\u0131n G\u00fcn\u00fc" },
            { "field.dayperiod", "AM/PM" },
            { "field.hour", "Saat" },
            { "field.minute", "Dakika" },
            { "field.second", "Saniye" },
            { "field.zone", "Saat Dilimi" },
        };
    }
}
